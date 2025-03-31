package com.example.hybridsearchspringboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.util.*;

@Service
public class BGETokenizer {
    private static final Logger logger = LoggerFactory.getLogger(BGETokenizer.class);
    private static final String VOCAB_PATH = "models/bge-small-zh-v1.5/vocab.txt";
    private static final String TOKENIZER_CONFIG_PATH = "models/bge-small-zh-v1.5/tokenizer_config.json";
    private static final String SPECIAL_TOKENS_PATH = "models/bge-small-zh-v1.5/special_tokens_map.json";
    
    private Map<String, Integer> vocab;
    private int maxLength;
    private int padTokenId;
    private int unkTokenId;
    private int clsTokenId;
    private int sepTokenId;
    private int maskTokenId;

    @PostConstruct
    public void init() throws IOException {
        try {
            // 加载词表
            ClassPathResource vocabResource = new ClassPathResource(VOCAB_PATH);
            vocab = new HashMap<>();
            try (Scanner scanner = new Scanner(vocabResource.getInputStream())) {
                int index = 0;
                while (scanner.hasNextLine()) {
                    String token = scanner.nextLine().trim();
                    vocab.put(token, index++);
                }
            }

            // 加载tokenizer配置
            ClassPathResource configResource = new ClassPathResource(TOKENIZER_CONFIG_PATH);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode config = mapper.readTree(configResource.getInputStream());
            
            // 设置最大长度
            maxLength = 512; // BGE-small-zh-v1.5 的默认最大长度

            // 加载特殊token
            ClassPathResource specialTokensResource = new ClassPathResource(SPECIAL_TOKENS_PATH);
            JsonNode specialTokens = mapper.readTree(specialTokensResource.getInputStream());
            
            padTokenId = vocab.get(specialTokens.get("pad_token").asText());
            unkTokenId = vocab.get(specialTokens.get("unk_token").asText());
            clsTokenId = vocab.get(specialTokens.get("cls_token").asText());
            sepTokenId = vocab.get(specialTokens.get("sep_token").asText());
            maskTokenId = vocab.get(specialTokens.get("mask_token").asText());

            logger.info("BGE Tokenizer加载成功");
        } catch (Exception e) {
            logger.error("BGE Tokenizer加载失败", e);
            throw new RuntimeException("BGE Tokenizer加载失败", e);
        }
    }

    public int[] tokenize(String text) {
        try {
            // 1. 添加特殊token
            List<Integer> tokenIds = new ArrayList<>();
            tokenIds.add(clsTokenId);

            // 2. 分词
            String[] tokens = text.split("\\s+");
            for (String token : tokens) {
                // 3. 转换为token ids
                Integer tokenId = vocab.get(token);
                if (tokenId == null) {
                    tokenId = unkTokenId;
                }
                tokenIds.add(tokenId);
            }

            // 4. 添加结束token
            tokenIds.add(sepTokenId);

            // 5. 截断或填充到指定长度
            int[] result = new int[maxLength];
            Arrays.fill(result, padTokenId);
            for (int i = 0; i < Math.min(tokenIds.size(), maxLength); i++) {
                result[i] = tokenIds.get(i);
            }

            return result;
        } catch (Exception e) {
            logger.error("分词失败: {}", e.getMessage(), e);
            throw new RuntimeException("分词失败", e);
        }
    }

    public int[] createAttentionMask(int[] tokenIds) {
        int[] attentionMask = new int[tokenIds.length];
        for (int i = 0; i < tokenIds.length; i++) {
            attentionMask[i] = tokenIds[i] == padTokenId ? 0 : 1;
        }
        return attentionMask;
    }
} 