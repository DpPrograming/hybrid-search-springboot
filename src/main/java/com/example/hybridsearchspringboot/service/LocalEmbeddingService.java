package com.example.hybridsearchspringboot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LocalEmbeddingService {
    private static final int VECTOR_DIMENSION = 384;

    @Autowired
    private BGEModelManager modelManager;

    public List<Double> embed(String text) {
        try {
            // 使用BGE模型生成向量
            float[] embedding = modelManager.generateEmbedding(text);
            
            // 转换为List<Double>
            List<Double> result = new ArrayList<>(VECTOR_DIMENSION);
            for (float value : embedding) {
                result.add((double) value);
            }
            
            return result;
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化失败", e);
        }
    }
} 