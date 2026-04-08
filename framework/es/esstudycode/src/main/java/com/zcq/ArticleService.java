package com.zcq;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ElasticsearchClient elasticsearchClient; // 原生客户端，用于复杂查询

    // ─── 基础 CRUD ────────────────────────────────────────────────────────────

    public Article save(Article article) {
        return articleRepository.save(article);
    }

    public Optional<Article> findById(String id) {
        return articleRepository.findById(id);
    }

    public Iterable<Article> findAll() {
        return articleRepository.findAll();
    }

    public void deleteById(String id) {
        articleRepository.deleteById(id);
    }

    // ─── 方法名推导查询 ───────────────────────────────────────────────────────

    public List<Article> searchByTitle(String keyword) {
        return articleRepository.findByTitleContaining(keyword);
    }

    public List<Article> findByAuthor(String author) {
        return articleRepository.findByAuthor(author);
    }

    // ─── 分页查询 ─────────────────────────────────────────────────────────────

    public Page<Article> findPage(int page, int size) {
        return articleRepository.findAll(PageRequest.of(page, size));
    }

    // ─── 原生 DSL 全文检索（multi_match） ────────────────────────────────────

    public List<Article> fullTextSearch(String keyword) throws IOException {
        SearchResponse<Article> response = elasticsearchClient.search(s -> s
                        .index("articles")
                        .query(q -> q
                                .multiMatch(m -> m
                                        .query(keyword)
                                        .fields("title", "content") // 同时搜索标题和正文
                                )
                        )
                        .size(20),
                Article.class
        );

        return response.hits().hits().stream()
                .map(Hit::source)
                .collect(Collectors.toList());
    }
}