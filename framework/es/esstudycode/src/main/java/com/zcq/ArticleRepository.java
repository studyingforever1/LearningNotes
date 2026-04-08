package com.zcq;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data ES Repository
 * 继承 ElasticsearchRepository 即可免费获得 CRUD + 分页 + 排序
 */
@Repository
public interface ArticleRepository extends ElasticsearchRepository<Article, String> {

    // 按标题关键词搜索（Spring Data 自动解析方法名）
    List<Article> findByTitleContaining(String keyword);

    // 按作者查询
    List<Article> findByAuthor(String author);

    // 按作者查询并按浏览量降序
    List<Article> findByAuthorOrderByViewsDesc(String author);
}