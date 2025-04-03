package com.example.hybridsearchspringboot.service;

import ai.onnxruntime.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import com.example.hybridsearchspringboot.tokenizer.BGETokenizer;

@Service
public class MiniLMEmbeddingService implements EmbeddingService {
    private static final Logger logger = LoggerFactory.getLogger(MiniLMEmbeddingService.class);
    private static final String MODEL_PATH = "models/paraphrase-multilingual-MiniLM-L12-v2/model.onnx";
    private static final int MAX_SEQUENCE_LENGTH = 128;
    private static final int VECTOR_DIMENSION = 384;

    @Autowired
    private BGETokenizer tokenizer;

    private OrtEnvironment env;
    private OrtSession session;
    private String[] inputNames;
    private String outputName;

    @PostConstruct
    @Override
    public void init() throws Exception {
        try {
            // 初始化ONNX Runtime环境
            env = OrtEnvironment.getEnvironment();
            
            // 加载模型文件
            ClassPathResource resource = new ClassPathResource(MODEL_PATH);
            Path tempFile = Files.createTempFile("model", ".onnx");
            Files.copy(resource.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            
            // 创建会话选项
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setIntraOpNumThreads(1);
            
            // 加载模型
            session = env.createSession(tempFile.toString(), sessionOptions);
            
            // 获取输入输出名称
            inputNames = session.getInputInfo().keySet().toArray(new String[0]);
            outputName = session.getOutputInfo().keySet().iterator().next();
            
            logger.info("paraphrase-multilingual-MiniLM-L12-v2模型加载成功");
        } catch (Exception e) {
            logger.error("模型加载失败", e);
            throw new RuntimeException("模型加载失败", e);
        }
    }

    @Override
    public float[] generateEmbedding(String text) throws Exception {
        try {
            // 1. 分词
            int[] tokenIds = tokenizer.tokenize(text);
            int[] attentionMask = tokenizer.createAttentionMask(tokenIds);

            // 2. 创建输入tensor
            long[] shape = new long[]{1, tokenIds.length};
            
            // 转换 int[] 为 LongBuffer
            LongBuffer inputBuffer = LongBuffer.allocate(tokenIds.length);
            LongBuffer maskBuffer = LongBuffer.allocate(attentionMask.length);
            for (int i = 0; i < tokenIds.length; i++) {
                inputBuffer.put(tokenIds[i]);
                maskBuffer.put(attentionMask[i]);
            }
            inputBuffer.flip();
            maskBuffer.flip();
            
            // 创建输入tensor
            OnnxTensor inputIds = OnnxTensor.createTensor(env, inputBuffer, shape);
            OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(env, maskBuffer, shape);

            // 3. 准备输入
            Map<String, OnnxTensor> inputs = Map.of(
                inputNames[0], inputIds,
                inputNames[1], attentionMaskTensor
            );

            // 4. 运行推理
            OrtSession.Result result = session.run(inputs);

            // 5. 获取输出并处理
            Object outputObj = result.get(0).getValue();
            float[][] output;
            
            // 处理不同的输出格式
            if (outputObj instanceof float[][]) {
                output = (float[][]) outputObj;
            } else if (outputObj instanceof float[][][]) {
                float[][][] output3D = (float[][][]) outputObj;
                output = output3D[0]; // 取第一个批次
            } else {
                logger.error("模型输出格式不支持: {}", outputObj.getClass().getName());
                throw new RuntimeException("模型输出格式不支持");
            }
            
            logger.info("模型输出形状: [{}][{}]", output.length, output[0].length);
            
            // 使用平均池化获取句子表示
            float[] embedding = new float[VECTOR_DIMENSION];
            for (int i = 0; i < output.length; i++) {
                float[] token = output[i];
                for (int j = 0; j < VECTOR_DIMENSION && j < token.length; j++) {
                    embedding[j] += token[j];
                }
            }
            
            // 计算平均值
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                embedding[i] /= output.length;
            }
            
            // 向量归一化 (L2范数)
            float norm = 0.0f;
            for (float value : embedding) {
                norm += value * value;
            }
            norm = (float) Math.sqrt(norm);
            
            if (norm > 0) {
                for (int i = 0; i < VECTOR_DIMENSION; i++) {
                    embedding[i] /= norm;
                }
            }
            
            // 检查向量是否包含 NaN
            boolean hasNaN = false;
            for (float value : embedding) {
                if (Float.isNaN(value)) {
                    hasNaN = true;
                    break;
                }
            }
            
            if (hasNaN) {
                logger.error("模型输出包含 NaN 值");
                throw new RuntimeException("模型输出包含 NaN 值");
            }
            
            // 验证向量维度
            if (embedding.length != VECTOR_DIMENSION) {
                logger.error("模型输出向量维度不正确: 期望 {} 维, 实际 {} 维", VECTOR_DIMENSION, embedding.length);
                throw new RuntimeException("模型输出向量维度不正确");
            }
            
            return embedding;
        } catch (Exception e) {
            logger.error("生成向量失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成向量失败", e);
        }
    }

    @Override
    public int getVectorDimension() {
        return VECTOR_DIMENSION;
    }
} 