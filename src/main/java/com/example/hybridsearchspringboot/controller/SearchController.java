package com.example.hybridsearchspringboot.controller;

import com.example.hybridsearchspringboot.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(@RequestParam String query) {
        try {
            Map<String, Object> results = searchService.hybridSearch(query);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error processing search request", e);
            return ResponseEntity.internalServerError().build();
        }
    }
} 