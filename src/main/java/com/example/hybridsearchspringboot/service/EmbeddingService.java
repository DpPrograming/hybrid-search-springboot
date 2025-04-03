package com.example.hybridsearchspringboot.service;

/**
 * 文本向量化服务接口
 */
public interface EmbeddingService {
    
    /**
     * 初始化模型
     * @throws Exception 初始化过程中的异常
     */
    void init() throws Exception;
    
    /**
     * 生成文本的向量表示
     * @param text 输入文本
     * @return 文本的向量表示
     * @throws Exception 向量生成过程中的异常
     */
    float[] generateEmbedding(String text) throws Exception;
    
    /**
     * 获取向量维度
     * @return 向量维度
     */
    int getVectorDimension();
} 