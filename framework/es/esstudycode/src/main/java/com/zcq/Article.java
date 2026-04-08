package com.zcq;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * 示例文档实体：文章
 * indexName 对应 ES 中的索引名称
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "articles")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Article {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String content;

    @Field(type = FieldType.Keyword)
    private String author;

    @Field(type = FieldType.Integer)
    private Integer views;
}
