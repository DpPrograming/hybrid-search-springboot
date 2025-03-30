package com.example.hybridsearchspringboot.config;

import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@Data
public class PromptConfig {

    @Value("${prompts.response-generation}")
    private String responseGenerationPrompt;

    @Value("${prompts.entity-expansion}")
    private String entityExpansionPrompt;
} 