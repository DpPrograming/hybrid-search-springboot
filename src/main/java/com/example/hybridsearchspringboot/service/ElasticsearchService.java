package com.example.hybridsearchspringboot.service;

import com.example.hybridsearchspringboot.model.SearchResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Service;

import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ElasticsearchService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ModelLoader modelLoader;
    private static final String INDEX_NAME = "new_movies_index";

    public Map<String, Object> hybridSearch(String query, int size) {
        try {
            Map<String, Object> response = new HashMap<>();
            
            // 执行文本搜索
            SearchResult textSearchResult = textSearch(query, size);
            response.put("textSearch", textSearchResult);
            
            // TODO: 执行向量搜索
            // List<Document> vectorResults = vectorSearch(...);
            // response.put("vectorSearch", vectorResults);
            
            // TODO: 混合排序逻辑
            // response.put("hybridResults", ...);
            
            // 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("timestamp", System.currentTimeMillis());
            metadata.put("queryType", "hybrid");
            response.put("metadata", metadata);
            
            return response;
        } catch (Exception e) {
            log.error("混合搜索失败", e);
            throw new RuntimeException("混合搜索失败", e);
        }
    }

    public SearchResult textSearch(String query, int size) {
        try {
            // 构建多字段搜索查询
            Criteria criteria = new Criteria();
            
            // 标题字段，最高权重
            criteria = criteria.or(new Criteria("title").matches(query).boost(3.0f));
            
            // 内容和简介字段，中等权重
            criteria = criteria.or(new Criteria("brief").matches(query).boost(2.0f));
            criteria = criteria.or(new Criteria("content").matches(query).boost(1.5f));
            
            // 演员和导演字段，较低权重
            criteria = criteria.or(new Criteria("actors").matches(query).boost(1.2f));
            criteria = criteria.or(new Criteria("directors").matches(query).boost(1.2f));
            
            // 标签和语言字段，基础权重
            criteria = criteria.or(new Criteria("tags").matches(query));
            criteria = criteria.or(new Criteria("voiceTags").matches(query));
            criteria = criteria.or(new Criteria("languages").matches(query));
            
            CriteriaQuery searchQuery = new CriteriaQuery(criteria);
            searchQuery.setMaxResults(size);

            // 执行搜索
            SearchHits<Map> searchHits = elasticsearchOperations.search(
                searchQuery,
                Map.class,
                IndexCoordinates.of(INDEX_NAME)
            );

            // 转换搜索结果为 Document 列表
            List<Map<String, Object>> documents = searchHits.getSearchHits().stream()
                .map(hit -> {
                    Map<String, Object> source = hit.getContent();
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", hit.getId());
                    doc.put("title", source.get("title"));
                    doc.put("brief", source.get("brief"));
                    doc.put("content", source.get("content"));
                    doc.put("actors", source.get("actors"));
                    doc.put("directors", source.get("directors"));
                    doc.put("tags", source.get("tags"));
                    doc.put("voiceTags", source.get("voiceTags"));
                    doc.put("languages", source.get("languages"));
                    doc.put("score", hit.getScore());
                    doc.put("vendor", source.get("vendor"));
                    doc.put("channel", source.get("channel"));
                    doc.put("publishYear", source.get("publishYear"));
                    doc.put("completed", source.get("completed"));
                    doc.put("total", source.get("total"));
                    doc.put("last", source.get("last"));
                    doc.put("updateTime", source.get("updateTime"));
                    return doc;
                })
                .collect(Collectors.toList());

            // 构建扩展信息
            Map<String, Object> entitiesAndExpansions = new HashMap<>();
            // TODO: 这里可以添加实体识别和查询扩展的逻辑

            // 构建结构化响应
            String structuredResponse = ""; // TODO: 可以添加结构化响应的生成逻辑

            // 构建并返回搜索结果
            return SearchResult.builder()
                .query(query)
                .entitiesAndExpansions(entitiesAndExpansions)
                .results(documents)
                .structuredResponse(structuredResponse)
                .build();

        } catch (Exception e) {
            log.error("文本搜索失败", e);
            throw new RuntimeException("文本搜索失败", e);
        }
    }

    public void indexDocument(String id, String title, String content) {
        try {
            Map<String, Object> document = Map.of(
                "title", title,
                "content", content
            );

            // 使用 ElasticsearchOperations 进行索引
            elasticsearchOperations.save(document, id, IndexCoordinates.of(INDEX_NAME));
        } catch (Exception e) {
            log.error("Error indexing document", e);
            throw new RuntimeException("Failed to index document", e);
        }
    }

    /**
     * 执行向量搜索
     */
    public List<Map<String, Object>> vectorSearch(float[] queryVector, Map<String, List<String>> filterFields) {
        try {
            // 构建基础的向量查询
            StringBuilder queryBuilder = new StringBuilder();
            // 格式化向量数组为正确的JSON格式
            String vectorJson = java.util.Arrays.toString(queryVector)
                .replace("[", "[")
                .replace("]", "]")
                .replace(" ", "");
                
            queryBuilder.append("""
                {
                    "script_score": {
                        "query": {"match_all": {}},
                        "script": {
                            "source": "cosineSimilarity(params.query_vector, 'text_vector') + 1.0",
                            "params": {
                                "query_vector": %s
                            }
                        }
                    }
                }""".formatted(vectorJson));

            // 如果有过滤条件，添加到查询中
            if (filterFields != null && !filterFields.isEmpty()) {
                // 将 script_score 查询包装在 bool 查询中
                String scriptQuery = queryBuilder.toString();
                queryBuilder = new StringBuilder();
                queryBuilder.append("""
                    {
                        "bool": {
                            "must": %s,
                            "filter": [
                    """.formatted(scriptQuery));
                
                List<String> filterClauses = new ArrayList<>();
                filterFields.forEach((field, values) -> {
                    if (values != null && !values.isEmpty()) {
                        values.forEach(value -> {
                            filterClauses.add(String.format(
                                "{\"match\": {\"%s\": {\"query\": \"%s\", \"boost\": %f}}}",
                                field,
                                value,
                                field.equals("title") ? 2.0f : 1.0f
                            ));
                        });
                    }
                });
                
                queryBuilder.append(String.join(",", filterClauses));
                queryBuilder.append("]}}");
            }

            StringQuery query = new StringQuery(queryBuilder.toString());

            // 执行搜索
            SearchHits<Map> searchHits = elasticsearchOperations.search(
                query,
                Map.class,
                IndexCoordinates.of(INDEX_NAME)
            );
            if (searchHits.getTotalHits() == 0) {
                return new ArrayList<>();
            }

            // 转换结果
            return searchHits.getSearchHits().stream()
                .map(hit -> {
                    Map<String, Object> source = hit.getContent();
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", hit.getId());
                    doc.put("title", source.get("title"));
                    doc.put("brief", source.get("brief"));
                    doc.put("content", source.get("content"));
                    doc.put("actors", source.get("actors"));
                    doc.put("directors", source.get("directors"));
                    doc.put("tags", source.get("tags"));
                    doc.put("voiceTags", source.get("voiceTags"));
                    doc.put("languages", source.get("languages"));
                    doc.put("score", hit.getScore());
                    doc.put("vendor", source.get("vendor"));
                    doc.put("channel", source.get("channel"));
                    doc.put("publishYear", source.get("publishYear"));
                    doc.put("completed", source.get("completed"));
                    doc.put("total", source.get("total"));
                    doc.put("last", source.get("last"));
                    doc.put("updateTime", source.get("updateTime"));
                    doc.put("sysTime", source.get("sysTime"));
                    doc.put("aid", source.get("aid"));
                    doc.put("vPic", source.get("vPic"));
                    doc.put("vPicMd5", source.get("vPicMd5"));
                    return doc;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量搜索失败", e);
            throw new RuntimeException("向量搜索失败", e);
        }
    }

    /**
     * 创建索引
     */
    public void createIndex(boolean deleteIfExists) {
        try {
            // 如果索引存在且需要删除
            if (deleteIfExists && elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME)).exists()) {
                elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
                log.info("删除已存在的索引: {}", INDEX_NAME);
            }

            // 创建索引配置
            Map<String, Object> settings = new HashMap<>();
            settings.put("index.number_of_shards", 1);
            settings.put("index.number_of_replicas", 1);
            settings.put("index.max_result_window", 10000);

            // 创建映射配置
            Map<String, Object> properties = new HashMap<>();
            
            // 文本字段
            Map<String, Object> textMapping = new HashMap<>();
            textMapping.put("type", "text");
            textMapping.put("analyzer", "standard");
            textMapping.put("search_analyzer", "standard");

            properties.put("title", textMapping);
            properties.put("brief", textMapping);
            properties.put("content", textMapping);

            // 关键词字段
            Map<String, Object> keywordMapping = new HashMap<>();
            keywordMapping.put("type", "keyword");

            properties.put("aid", keywordMapping);
            properties.put("vendor", keywordMapping);
            properties.put("channel", keywordMapping);
            properties.put("publishYear", keywordMapping);
            properties.put("vPic", keywordMapping);
            properties.put("vPicMd5", keywordMapping);

            // 数组字段
            properties.put("directors", keywordMapping);
            properties.put("actors", keywordMapping);
            properties.put("languages", keywordMapping);
            properties.put("tags", keywordMapping);
            properties.put("voiceTags", keywordMapping);

            // 数值字段
            properties.put("score", Map.of("type", "float"));
            properties.put("total", Map.of("type", "integer"));
            properties.put("last", Map.of("type", "integer"));

            // 布尔字段
            properties.put("completed", Map.of("type", "boolean"));

            // 日期字段
            Map<String, Object> dateMapping = new HashMap<>();
            dateMapping.put("type", "date");
            dateMapping.put("format", "yyyy-MM-dd||yyyy-MM-dd HH:mm:ss||epoch_millis");

            properties.put("updateTime", dateMapping);
            properties.put("sysTime", dateMapping);

            // 向量字段
            Map<String, Object> vectorMapping = new HashMap<>();
            vectorMapping.put("type", "dense_vector");
            vectorMapping.put("dims", 384);
            vectorMapping.put("index", true);
            vectorMapping.put("similarity", "cosine");

            properties.put("text_vector", vectorMapping);

            // 创建映射
            Document mapping = Document.from(Map.of("properties", properties));

            // 应用设置和映射
            elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME))
                .create(settings, mapping);

            log.info("成功创建索引: {}", INDEX_NAME);
        } catch (Exception e) {
            log.error("创建索引失败", e);
            throw new RuntimeException("创建索引失败", e);
        }
    }

    /**
     * 批量索引文档
     */
    public void bulkIndexDocuments(List<Map<String, Object>> documents) {
        try {
            List<IndexQuery> queries = documents.stream()
                .map(doc -> {
                    // 生成文本向量
                    List<String> textParts = new ArrayList<>();
                    if (doc.get("title") != null) textParts.add((String) doc.get("title"));
                    if (doc.get("brief") != null) textParts.add((String) doc.get("brief"));
                    
                    // 添加数组字段
                    addListToTextParts(textParts, (List<String>) doc.get("actors"));
                    addListToTextParts(textParts, (List<String>) doc.get("directors"));
                    addListToTextParts(textParts, (List<String>) doc.get("tags"));
                    addListToTextParts(textParts, (List<String>) doc.get("voiceTags"));

                    // 生成向量并添加到文档
                    if (!textParts.isEmpty()) {
                        String combinedText = String.join(" ", textParts);
                        float[] vector = modelLoader.textToVector(combinedText);
                        log.info("生成向量维度: {}, 文档ID: {}", vector.length, doc.get("aid"));
                        doc.put("text_vector", vector);
                    }

                    // 创建索引请求
                    IndexQueryBuilder queryBuilder = new IndexQueryBuilder();
                    queryBuilder.withId((String) doc.get("aid"));
                    queryBuilder.withIndex(INDEX_NAME);
                    queryBuilder.withObject(doc);
                    return queryBuilder.build();
                })
                .collect(Collectors.toList());

            elasticsearchOperations.bulkIndex(queries, IndexCoordinates.of(INDEX_NAME));
            log.info("批量索引完成，处理了 {} 条文档", documents.size());
        } catch (Exception e) {
            log.error("批量索引文档失败", e);
            throw new RuntimeException("批量索引文档失败", e);
        }
    }

    private void addListToTextParts(List<String> textParts, List<String> items) {
        if (items != null && !items.isEmpty()) {
            textParts.addAll(items);
        }
    }
} 