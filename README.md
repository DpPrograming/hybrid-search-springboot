# Hybrid Search with Spring Boot

这是一个基于 Spring Boot 的混合搜索系统，集成了文本搜索和向量搜索功能。系统使用 Elasticsearch 作为搜索引擎，支持文本相似度搜索和向量相似度搜索，并可以将两种搜索结果进行混合排序。

## 功能特点

- 文本搜索：支持多字段加权搜索，包括标题、内容、简介、演员、导演等字段
- 向量搜索：使用文本向量进行语义相似度搜索
- 混合搜索：结合文本搜索和向量搜索的结果，提供更准确的搜索结果
- 灵活的过滤条件：支持多字段过滤和权重调整

## 技术栈

- Spring Boot
- Elasticsearch
- Spring Data Elasticsearch
- Java 17+

## 环境要求

- JDK 17 或更高版本
- Elasticsearch 8.x
- Maven 3.6+

## 快速开始

1. 克隆项目
```bash
git clone [your-repository-url]
cd hybrid-search-springboot
```

2. 配置 Elasticsearch
- 确保 Elasticsearch 服务已启动
- 在 `application.properties` 中配置 Elasticsearch 连接信息

3. 构建项目
```bash
mvn clean install
```

4. 运行项目
```bash
mvn spring-boot:run
```

## 项目结构

```
src/main/java/com/example/hybridsearchspringboot/
├── controller/
│   └── SearchController.java    # 搜索接口控制器
├── service/
│   ├── ElasticsearchService.java    # Elasticsearch 操作服务
│   ├── ModelLoader.java             # 向量模型加载器
│   └── SearchService.java           # 搜索业务逻辑服务
├── model/
│   └── SearchResult.java       # 搜索结果模型
└── HybridSearchSpringbootApplication.java
```

## API 接口

### 混合搜索
```
GET /api/search
参数：
- query: 搜索关键词
- size: 返回结果数量
```

### 文本搜索
```
GET /api/search/text
参数：
- query: 搜索关键词
- size: 返回结果数量
```

### 向量搜索
```
GET /api/search/vector
参数：
- query: 搜索关键词
- size: 返回结果数量
- filterFields: 过滤条件（可选）
```

## 配置说明

主要配置项在 `application.properties` 中：

```properties
# Elasticsearch 配置
spring.elasticsearch.rest.uris=http://localhost:9200
spring.elasticsearch.rest.username=elastic
spring.elasticsearch.rest.password=your-password

# 索引配置
elasticsearch.index.name=hybrid_search
```

## 开发说明

1. 索引创建
- 系统启动时会自动创建索引（如果不存在）
- 索引包含文本字段和向量字段
- 向量维度为 1536

2. 数据索引
- 支持批量索引文档
- 自动生成文本向量
- 支持更新和删除操作

3. 搜索实现
- 文本搜索使用多字段加权匹配
- 向量搜索使用余弦相似度计算
- 混合搜索支持结果合并和排序

## 注意事项

1. 确保 Elasticsearch 服务正常运行
2. 向量搜索需要足够的计算资源
3. 建议在生产环境中配置适当的索引分片和副本数

## 贡献指南

1. Fork 项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建 Pull Request

## 许可证

[MIT License](LICENSE) 