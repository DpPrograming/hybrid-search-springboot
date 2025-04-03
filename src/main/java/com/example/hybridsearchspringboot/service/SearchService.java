package com.example.hybridsearchspringboot.service;

import com.example.hybridsearchspringboot.model.SearchResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class SearchService {

    private final ElasticsearchService elasticsearchService;
    private final ModelLoader modelLoader;
    private final ObjectMapper objectMapper;
    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 执行混合搜索
     */
    public SearchResponse hybridSearch(String query, int size) {
        try {
            // 生成查询向量
            float[] queryVector = embeddingService.generateEmbedding(query);
            
            // 执行向量搜索
            List<SearchResult> results = elasticsearchService.vectorSearch(queryVector, size);
            
            // 生成结构化响应
            String structuredResponse = modelLoader.generateStructuredResponse(query, results);
            
            // 解析结构化响应
            JsonNode jsonNode = objectMapper.readTree(structuredResponse);
            
            // 构建返回结果
            SearchResponse response = new SearchResponse();
            response.setSummary(jsonNode.get("summary").asText());
            
            // 处理推荐结果
            List<Recommendation> recommendations = new ArrayList<>();
            JsonNode recommendationsNode = jsonNode.get("recommendations");
            if (recommendationsNode != null && recommendationsNode.isArray()) {
                for (JsonNode recNode : recommendationsNode) {
                    Recommendation rec = new Recommendation();
                    rec.setAid(recNode.get("aid").asText());
                    rec.setTitle(recNode.get("title").asText());
                    rec.setScore(recNode.get("score").asDouble());
                    rec.setPoster(recNode.get("poster").asText());
                    recommendations.add(rec);
                }
            }
            response.setRecommendations(recommendations);
            
            // 处理建议问题
            List<String> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = jsonNode.get("suggestions");
            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (JsonNode sugNode : suggestionsNode) {
                    suggestions.add(sugNode.asText());
                }
            }
            response.setSuggestions(suggestions);
            
            // 添加时间信息
            response.setSearchTime(System.currentTimeMillis());
            
            return response;
        } catch (Exception e) {
            log.error("混合搜索失败", e);
            throw new RuntimeException("混合搜索失败", e);
        }
    }
} 