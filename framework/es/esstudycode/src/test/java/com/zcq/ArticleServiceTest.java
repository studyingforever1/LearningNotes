package com.zcq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@SpringBootTest
class ArticleServiceTest {

    @Autowired
    private ArticleService articleService;

    @Test
    void testSaveAndSearch() throws IOException {
        // 1. 保存两篇文章
        Article a1 = Article.builder()
                .id(UUID.randomUUID().toString())
                .title("Elasticsearch 入门指南")
                .content("Elasticsearch 是一个分布式搜索引擎，基于 Lucene 构建。")
                .author("张三")
                .views(100)
                .build();

        Article a2 = Article.builder()
                .id(UUID.randomUUID().toString())
                .title("Spring Boot 整合 ES 实战")
                .content("通过 Spring Data Elasticsearch 可以快速实现 ES 的 CRUD 操作。")
                .author("李四")
                .views(200)
                .build();

        articleService.save(a1);
        articleService.save(a2);
        System.out.println("✅ 保存成功");

        // 2. 根据标题搜索
        List<Article> results = articleService.searchByTitle("Elasticsearch");
        System.out.println("🔍 标题搜索结果数量：" + results.size());
        results.forEach(a -> System.out.println("  - " + a.getTitle()));

        // 3. 全文检索
        List<Article> ftResults = articleService.fullTextSearch("分布式搜索");
        System.out.println("🔍 全文检索结果数量：" + ftResults.size());
        ftResults.forEach(a -> System.out.println("  - " + a.getTitle()));
    }
}