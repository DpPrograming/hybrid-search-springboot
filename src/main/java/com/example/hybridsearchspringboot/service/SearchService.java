package com.example.hybridsearchspringboot.service;

import com.example.hybridsearchspringboot.model.SearchResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class SearchService {

    private final ElasticsearchService elasticsearchService;
    private final ModelLoader modelLoader;

    /**
     * 执行混合搜索
     */
    public Map<String, Object> hybridSearch(String query) {
        long startTime = System.currentTimeMillis();
        try {
            // 1. 识别查询中的实体并生成扩展词
            long entityStartTime = System.currentTimeMillis();
            Map<String, Object> entitiesAndExpansions = modelLoader.recognizeEntitiesAndExpansions(query);
            long entityTime = System.currentTimeMillis() - entityStartTime;
            log.info("实体识别和扩展词生成结果: {}", entitiesAndExpansions);
            log.info("实体识别和语义扩展耗时: {}秒", entityTime / 1000.0);

            // 分离实体和扩展词
            Map<String, List<String>> entities = new HashMap<>();
            entities.put("title", new ArrayList<>((List<String>) entitiesAndExpansions.getOrDefault("title", List.of())));
            entities.put("languages", new ArrayList<>((List<String>) entitiesAndExpansions.getOrDefault("languages", List.of())));
            entities.put("actors", new ArrayList<>((List<String>) entitiesAndExpansions.getOrDefault("actors", List.of())));
            entities.put("directors", new ArrayList<>((List<String>) entitiesAndExpansions.getOrDefault("directors", List.of())));
            List<String> expansions = new ArrayList<>((List<String>) entitiesAndExpansions.getOrDefault("expansion_terms", List.of()));

            // 如果没有识别到title实体，使用扩展词作为title
            if (entities.get("title").isEmpty()) {
                entities.put("title", expansions);
                log.info("使用扩展词作为title实体: {}", expansions);
            }

            // 2. 将扩展词转换为向量并执行搜索
            long esSearchStartTime = System.currentTimeMillis();
            // 由扩展词用扩展词查，没有用用户输入查
            String combinedQuery = expansions.isEmpty() ? query : String.join(" ", expansions);
            log.info("向量查询拼接结果: {}", combinedQuery);
            float[] queryVector = modelLoader.textToVector(combinedQuery);
            List<Map<String, Object>> searchResults = elasticsearchService.vectorSearch(queryVector, entities);
            long esSearchTime = System.currentTimeMillis() - esSearchStartTime;
            log.info("ES搜索耗时: {}秒", esSearchTime / 1000.0);

            // 3. 处理搜索结果
            long processStartTime = System.currentTimeMillis();
            List<Map<String, Object>> processedResults = searchResults.stream()
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("title", doc.getOrDefault("title", ""));
                    result.put("score", doc.getOrDefault("score", 0.0));
                    result.put("aid", doc.getOrDefault("aid", ""));
                    result.put("brief", doc.getOrDefault("brief", ""));
                    result.put("directors", doc.getOrDefault("directors", List.of()));
                    result.put("actors", doc.getOrDefault("actors", List.of()));
                    result.put("languages", doc.getOrDefault("languages", List.of()));
                    result.put("tags", doc.getOrDefault("tags", List.of()));
                    result.put("voiceTags", doc.getOrDefault("voiceTags", List.of()));
                    result.put("vendor", doc.getOrDefault("vendor", ""));
                    result.put("publishYear", doc.getOrDefault("publishYear", ""));
                    result.put("channel", doc.getOrDefault("channel", ""));
                    result.put("vPic", doc.getOrDefault("vPic", ""));
                    result.put("vPicMd5", doc.getOrDefault("vPicMd5", ""));
                    result.put("completed", doc.getOrDefault("completed", false));
                    result.put("total", doc.getOrDefault("total", 0));
                    result.put("last", doc.getOrDefault("last", 0));
                    result.put("updateTime", doc.getOrDefault("updateTime", ""));
                    result.put("sysTime", doc.getOrDefault("sysTime", ""));
                    return result;
                })
                .collect(Collectors.toList());
            long processTime = System.currentTimeMillis() - processStartTime;
            log.info("结果处理耗时: {}秒", processTime / 1000.0);

            // 4. 生成结构化响应
            String structuredResponse = modelLoader.generateStructuredResponse(query, Map.of(
                "entities", entities,
                "expansions", expansions,
                "total", processedResults.size(),
                "results", processedResults
            ));
            log.info("结构化响应生成完成");

            // 5. 计算总耗时
            long totalTime = System.currentTimeMillis() - startTime;

            // 6. 构建返回结果
            Map<String, Object> response = new HashMap<>();
            response.put("query", query);
            response.put("entities", entities);
            response.put("expansions", expansions);
            response.put("results", processedResults);
            response.put("structuredResponse", structuredResponse);
            response.put("total", processedResults.size());
            response.put("timing", Map.of(
                "entity_recognition_and_expansion", entityTime / 1000.0,
                "es_search", esSearchTime / 1000.0,
                "result_processing", processTime / 1000.0,
                "total", totalTime / 1000.0
            ));
            response.put("timestamp", System.currentTimeMillis());
            
            return response;
        } catch (Exception e) {
            log.error("混合搜索失败", e);
            throw new RuntimeException("混合搜索失败", e);
        }
    }
} 