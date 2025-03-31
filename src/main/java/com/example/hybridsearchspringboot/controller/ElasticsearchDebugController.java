package com.example.hybridsearchspringboot.controller;

import com.example.hybridsearchspringboot.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.StringQuery;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/debug/es")
@RequiredArgsConstructor
public class ElasticsearchDebugController {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchService elasticsearchService;
    private static final String INDEX_NAME = "new_movies_index";

    @PostMapping("/raw-query")
    public SearchHits<Map> executeRawQuery(@RequestBody String query) {
        log.info("执行原生查询: {}", query);
        return elasticsearchOperations.search(
            new StringQuery(query),
            Map.class,
            IndexCoordinates.of(INDEX_NAME)
        );
    }

    @PostMapping("/index-document")
    public void indexTestDocument(
            @RequestParam String id,
            @RequestParam String title,
            @RequestParam String content) {
        log.info("索引测试文档 - id: {}, title: {}", id, title);
        elasticsearchService.indexDocument(id, title, content);
    }

    @PostMapping("/vector-search")
    public Object testVectorSearch(
            @RequestParam(required = false, defaultValue = "5") int dimensions,
            @RequestParam(required = false, defaultValue = "0.5") float defaultValue) {
        log.info("测试向量搜索 - dimensions: {}, defaultValue: {}", dimensions, defaultValue);
        try {
            // 创建测试向量
            float[] testVector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                testVector[i] = defaultValue;
            }
            return elasticsearchService.vectorSearch(testVector, null);
        } catch (Exception e) {
            log.error("向量搜索测试失败", e);
            return Map.of(
                "error", e.getMessage(),
                "stackTrace", e.getStackTrace()
            );
        }
    }

    @GetMapping("/index-info")
    public Map<String, Object> getIndexInfo() {
        try {
            boolean exists = elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME))
                .exists();
            
            Map<String, Object> indexSettings = elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME))
                .getSettings();
            
            Map<String, Object> indexMapping = elasticsearchOperations.indexOps(IndexCoordinates.of(INDEX_NAME))
                .getMapping();
            
            return Map.of(
                "exists", exists,
                "settings", indexSettings,
                "mapping", indexMapping
            );
        } catch (Exception e) {
            log.error("获取索引信息失败", e);
            return Map.of("error", e.getMessage());
        }
    }

    @PostMapping("/text-search")
    public Object testTextSearch(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "10") int size) {
        log.info("测试文本搜索 - query: {}, size: {}", query, size);
        try {
            return elasticsearchService.textSearch(query, size);
        } catch (Exception e) {
            log.error("文本搜索测试失败", e);
            return Map.of(
                "error", e.getMessage(),
                "stackTrace", e.getStackTrace()
            );
        }
    }

    @PostMapping("/init-index")
    public Map<String, Object> initIndex(
            @RequestParam(defaultValue = "true") boolean recreateIndex) {
        log.info("开始初始化索引 - recreateIndex: {}", recreateIndex);
        try {
            elasticsearchService.createIndex(recreateIndex);
            return Map.of(
                "success", true,
                "message", "索引初始化完成"
            );
        } catch (Exception e) {
            log.error("索引初始化失败", e);
            return Map.of(
                "success", false,
                "message", "索引初始化失败: " + e.getMessage()
            );
        }
    }

    @PostMapping("/init-test-data")
    public Map<String, Object> initTestData(
            @RequestParam(defaultValue = "false") boolean recreateIndex) {
        log.info("开始初始化测试数据");
        try {
            // 总是重新创建索引以确保映射正确
            elasticsearchService.createIndex(true);
            log.info("索引重新创建完成");

            // 生成测试数据
            List<Map<String, Object>> testData = Arrays.asList(
                createMovieData(
                    "movie_001", "流浪地球2",
                    "在不久的将来，太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新的家园。然而宇宙之路危机四伏，为了拯救地球，流浪地球时代的年轻人再次挺身而出，展开争分夺秒的生死之战。",
                    List.of("郭帆"),
                    List.of("吴京", "刘德华", "李雪健", "沙溢", "宁理", "王智"),
                    List.of("中文", "英文"),
                    List.of("科幻", "冒险", "灾难"),
                    List.of("国语", "英语"),
                    "中国电影", "2023", "电影",
                    "https://example.com/wandering-earth-2.jpg", "md5_hash_1",
                    "2023-01-22", "2023-01-22 10:00:00"
                ),
                createMovieData(
                    "movie_002", "满江红",
                    "南宋绍兴年间，岳飞死后四年，秦桧率领的奸臣集团已权倾朝野。以张大善人为首的反秦势力被朝廷围剿，更有神秘势力暗中揭露着一个可能改变整个大宋的惊天秘密。",
                    List.of("张艺谋"),
                    List.of("沈腾", "易烊千玺", "张译", "雷佳音", "岳云鹏"),
                    List.of("中文"),
                    List.of("悬疑", "剧情", "古装"),
                    List.of("国语"),
                    "中国电影", "2023", "电影",
                    "https://example.com/full-river-red.jpg", "md5_hash_2",
                    "2023-01-22", "2023-01-22 10:00:00"
                ),
                createMovieData(
                    "movie_003", "独行月球",
                    "人类为抵御小行星的撞击，拯救地球，在月球部署了大量引力装置。但由于意外，月球被引力装置推离原始轨道，向地球高速落去。人类面临绝境，而独在月球的特殊人群成为了拯救地球的最后希望。",
                    List.of("张吃鱼"),
                    List.of("沈腾", "马丽", "常远", "李诞"),
                    List.of("中文"),
                    List.of("科幻", "喜剧"),
                    List.of("国语"),
                    "中国电影", "2022", "电影",
                    "https://example.com/moon-man.jpg", "md5_hash_3",
                    "2022-07-29", "2022-07-29 10:00:00"
                ),
                createMovieData(
                    "movie_004", "长津湖",
                    "1950年，朝鲜战争爆发，中国人民志愿军赴朝作战。七连战士伍千里、伍万里等人在长津湖地区奉命坚守阵地，面对极寒天气和美军王牌部队的进攻，他们以钢铁意志和英勇无畏的战斗精神，谱写了一曲英雄赞歌。",
                    List.of("陈凯歌", "徐克", "林超贤"),
                    List.of("吴京", "易烊千玺", "朱亚文", "李晨", "胡军"),
                    List.of("中文"),
                    List.of("战争", "历史", "剧情"),
                    List.of("国语"),
                    "中国电影", "2021", "电影",
                    "https://example.com/battle-at-lake-changjin.jpg", "md5_hash_4",
                    "2021-09-30", "2021-09-30 10:00:00"
                ),
                createMovieData(
                    "movie_005", "你好，李焕英",
                    "一场意外让贾晓玲穿越回到了1981年，与年轻的母亲李焕英相遇。她努力改变母亲的命运，却发现一切并不是想象的那么简单。",
                    List.of("贾玲"),
                    List.of("贾玲", "张小斐", "沈腾", "陈赫"),
                    List.of("中文"),
                    List.of("喜剧", "剧情", "奇幻"),
                    List.of("国语"),
                    "中国电影", "2021", "电影",
                    "https://example.com/hi-mom.jpg", "md5_hash_5",
                    "2021-02-12", "2021-02-12 10:00:00"
                )
            );

            // 批量索引测试数据
            elasticsearchService.bulkIndexDocuments(testData);

            return Map.of(
                "success", true,
                "message", "测试数据初始化完成",
                "count", testData.size()
            );
        } catch (Exception e) {
            log.error("初始化测试数据失败", e);
            return Map.of(
                "success", false,
                "message", "初始化测试数据失败: " + e.getMessage()
            );
        }
    }

    private Map<String, Object> createMovieData(
            String aid, String title, String brief,
            List<String> directors, List<String> actors,
            List<String> languages, List<String> tags,
            List<String> voiceTags, String vendor,
            String publishYear, String channel,
            String vPic, String vPicMd5,
            String updateTime, String sysTime) {
        Map<String, Object> movie = new HashMap<>();
        movie.put("aid", aid);
        movie.put("title", title);
        movie.put("brief", brief);
        movie.put("directors", directors);
        movie.put("actors", actors);
        movie.put("languages", languages);
        movie.put("tags", tags);
        movie.put("voiceTags", voiceTags);
        movie.put("vendor", vendor);
        movie.put("publishYear", publishYear);
        movie.put("channel", channel);
        movie.put("vPic", vPic);
        movie.put("vPicMd5", vPicMd5);
        movie.put("completed", true);
        movie.put("total", 1);
        movie.put("last", 1);
        movie.put("updateTime", updateTime);
        movie.put("sysTime", sysTime);
        return movie;
    }
} 