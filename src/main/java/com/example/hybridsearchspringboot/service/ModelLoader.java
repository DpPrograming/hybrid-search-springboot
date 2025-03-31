package com.example.hybridsearchspringboot.service;

import com.example.hybridsearchspringboot.config.PromptConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class ModelLoader {

    private final LocalEmbeddingService embeddingService;
    private final ChatModel chatModel;
    private final PromptConfig promptConfig;
    private final ObjectMapper objectMapper;

    /**
     * 识别查询中的实体并生成扩展词
     */
    public Map<String, Object> recognizeEntitiesAndExpansions(String query) {
        try {
            String prompt = String.format(promptConfig.getEntityExpansionPrompt(), query);
            log.debug("发送到 LLM 的提示词: {}", prompt);
            
            String response = ChatClient.create(chatModel)
                    .prompt(prompt)
                    .user(query)
                    .call()
                    .content();
            
            log.debug("LLM 返回的原始响应: {}", response);
            
            // 处理可能包含的Markdown代码块
            if (response.startsWith("```")) {
                String[] lines = response.split("\n");
                if (lines[lines.length - 1].trim().equals("```")) {
                    response = String.join("\n", List.of(lines).subList(1, lines.length - 1));
                } else {
                    response = String.join("\n", List.of(lines).subList(1, lines.length));
                }
                log.debug("处理后的响应: {}", response);
            }

            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            log.debug("解析后的 JSON 结果: {}", result);
            return result;
        } catch (Exception e) {
            log.error("实体识别和扩展词生成失败", e);
            return Map.of(
                "title", List.of(),
                "languages", List.of(),
                "actors", List.of(),
                "directors", List.of(),
                "expansion_terms", List.of()
            );
        }
    }

    /**
     * 将文本转换为向量
     * @return
     */
    public float[] textToVector(String text) {
        try {
            List<Double> embedding = embeddingService.embed(text);
            // 将 List<Double> 转换为 float[]
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.error("文本向量化失败", e);
            throw new RuntimeException("文本向量化失败", e);
        }
    }

    /**
     * 生成结构化响应
     */
    public String generateStructuredResponse(String query, Map<String, Object> searchResults) {
        try {
            // 处理搜索结果，只保留需要的字段
            List<Map<String, Object>> results = ((List<Map<String, Object>>) searchResults.get("results")).stream()
                .map(r -> {
                    Map<String, Object> filtered = new HashMap<>();
                    filtered.put("aid", r.get("aid"));
                    filtered.put("title", r.get("title"));
                    filtered.put("brief", r.get("brief"));
                    filtered.put("directors", r.get("directors"));
                    filtered.put("actors", r.get("actors"));
                    filtered.put("languages", r.get("languages"));
                    
                    // 修改 tags 处理逻辑，增加空值检查
                    List<String> tags = new ArrayList<>();
                    if (r.get("tags") instanceof List) {
                        tags.addAll((List<String>) r.get("tags"));
                    }
                    if (r.get("voiceTags") instanceof List) {
                        tags.addAll((List<String>) r.get("voiceTags"));
                    }
                    filtered.put("tags", new ArrayList<>(new HashSet<>(tags))); // 去重
                    
                    filtered.put("publishYear", r.get("publishYear"));
                    return filtered;
                })
                .collect(Collectors.toList());

            String prompt = String.format(promptConfig.getResponseGenerationPrompt(),
                query,
                objectMapper.writeValueAsString(searchResults.get("entities")),
                objectMapper.writeValueAsString(searchResults.get("expansions")),
                searchResults.get("total"),
                objectMapper.writeValueAsString(results)
            );
            
            log.debug("发送到 LLM 的提示词: {}", prompt);
            
            String response = ChatClient.create(chatModel)
                    .prompt(prompt)
                    .user(query)
                    .call()
                    .content();
            
            log.debug("LLM 返回的响应: {}", response);
            
            // 处理可能包含的Markdown代码块
            if (response.startsWith("```")) {
                String[] lines = response.split("\n");
                if (lines[lines.length - 1].trim().equals("```")) {
                    response = String.join("\n", List.of(lines).subList(1, lines.length - 1));
                } else {
                    response = String.join("\n", List.of(lines).subList(1, lines.length));
                }
                log.debug("处理后的响应: {}", response);
            }
            
            // 验证响应是否为有效的JSON
            try {
                objectMapper.readValue(response, Map.class);
                return response;
            } catch (Exception e) {
                log.error("LLM返回的不是有效的JSON格式: {}", response);
                // 如果解析失败，返回一个默认的错误响应
                return "{\"summary\":\"抱歉，生成响应时出现错误。\",\"recommendations\":[],\"suggestion\":[]}";
            }
        } catch (Exception e) {
            log.error("生成结构化响应失败", e);
            return "{\"summary\":\"抱歉，生成响应时出现错误。\",\"recommendations\":[],\"suggestion\":[]}";
        }
    }
} 