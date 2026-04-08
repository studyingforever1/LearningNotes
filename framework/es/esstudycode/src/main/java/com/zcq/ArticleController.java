package com.zcq;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    /** 新增 / 更新文档 */
    @PostMapping
    public ResponseEntity<Article> save(@RequestBody Article article) {
        return ResponseEntity.ok(articleService.save(article));
    }

    /** 根据 ID 查询 */
    @GetMapping("/{id}")
    public ResponseEntity<Article> getById(@PathVariable String id) {
        return articleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 查询全部 */
    @GetMapping
    public ResponseEntity<Iterable<Article>> getAll() {
        return ResponseEntity.ok(articleService.findAll());
    }

    /** 根据标题关键词搜索（方法名推导） */
    @GetMapping("/search/title")
    public ResponseEntity<List<Article>> searchByTitle(@RequestParam String keyword) {
        return ResponseEntity.ok(articleService.searchByTitle(keyword));
    }

    /** 根据作者查询 */
    @GetMapping("/search/author")
    public ResponseEntity<List<Article>> searchByAuthor(@RequestParam String author) {
        return ResponseEntity.ok(articleService.findByAuthor(author));
    }

    /** 全文检索（multi_match，原生 DSL） */
    @GetMapping("/search/fulltext")
    public ResponseEntity<List<Article>> fullText(@RequestParam String keyword) throws IOException {
        return ResponseEntity.ok(articleService.fullTextSearch(keyword));
    }

    /** 分页查询 */
    @GetMapping("/page")
    public ResponseEntity<Page<Article>> page(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(articleService.findPage(page, size));
    }

    /** 删除文档 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        articleService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}