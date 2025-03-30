package com.example.hybridsearchspringboot.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchResult {
    private String query;
    private Map<String, Object> entitiesAndExpansions;
    private List<Map<String, Object>> results;
    private String structuredResponse;
} 