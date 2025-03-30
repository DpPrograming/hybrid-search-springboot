package com.example.hybridsearchspringboot.service;

import com.example.hybridsearchspringboot.config.PromptConfig;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@AllArgsConstructor
public class ModelLoader {

    private final EmbeddingModel embeddingModel;
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
            return embeddingModel.embed(text);
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
            String prompt = String.format(promptConfig.getResponseGenerationPrompt(),
                query,
                objectMapper.writeValueAsString(searchResults.get("entities")),
                objectMapper.writeValueAsString(searchResults.get("expansions")),
                searchResults.get("total"),
                objectMapper.writeValueAsString(searchResults.get("results"))
            );
            
            log.debug("发送到 LLM 的提示词: {}", prompt);
            
            String response = ChatClient.create(chatModel)
                    .prompt(prompt)
                    .user(query)
                    .call()
                    .content();
            
            log.debug("LLM 返回的响应: {}", response);
            return response;
        } catch (Exception e) {
            log.error("生成结构化响应失败", e);
            return "抱歉，生成响应时出现错误。";
        }
    }
} 