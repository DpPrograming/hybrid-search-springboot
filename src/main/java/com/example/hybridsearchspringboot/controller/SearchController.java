package com.example.hybridsearchspringboot.controller;

import com.example.hybridsearchspringboot.service.SearchService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@Slf4j
@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping(value = "/search")
    public ResponseEntity<Map<String, Object>> search(@RequestBody SearchRequest request) {
        try {
            Map<String, Object> results = searchService.hybridSearch(request.getQuery());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error processing search request", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/search")
    public ResponseEntity<Map<String, Object>> getSearch(@RequestParam String request) {
        try {
            Map<String, Object> results = searchService.hybridSearch(request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error processing search request", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

@Data
class SearchRequest {
    private String query;
} 