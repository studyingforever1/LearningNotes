package com.zcq;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.client.indices.GetIndexResponse;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.common.unit.Fuzziness;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;

import javax.swing.text.Highlighter;
import java.io.IOException;
import java.util.Map;
import java.util.TimeZone;

public class Main {
    public static RestHighLevelClient client = new RestHighLevelClient(
            RestClient.builder(new HttpHost("localhost", 9200, "http")));
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static String writeValueAsString(Object value) throws IOException {
        return objectMapper.writeValueAsString(value);
    }

    static {
        //序列化的时候序列对象的所有属性
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        //忽略字段不匹配错误
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        //如果是空对象的时候,不抛异常
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        TimeZone timeZone = TimeZone.getDefault();//系统默认时区
        objectMapper.setTimeZone(timeZone);
    }

    public static void main(String[] args) throws IOException {
//        createIndex();
//        getIndex();
//        delIndex();

//        createDoc();

//        updateDoc();

//        getDoc();
//        deleteDoc();
//        batchInsert();
//        batchDelete();

//        queryAll();
//        queryByCondition();
//        queryByConditionWithPage();
//        queryByConditionWithPageAndSort();
//        queryByConditionWithPageAndSortAndInclude();
//        queryByConditionWithPageAndSortAndIncludeAndBool();
//        queryByConditionWithPageAndSortAndIncludeAndBoolAndRange();
//        queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndFuzzy();
//        queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndHighLight();
        queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndHighLightAndAggr();
        client.close();
    }

    public static void createIndex() throws IOException {
        CreateIndexResponse createIndexResponse = client.indices()
                .create(new CreateIndexRequest("user"), RequestOptions.DEFAULT);
        System.out.println(createIndexResponse.isAcknowledged());
    }

    public static void getIndex() throws IOException {
        GetIndexResponse getIndexResponse = client.indices().get(new GetIndexRequest("user"), RequestOptions.DEFAULT);
        System.out.println(objectMapper.writeValueAsString(getIndexResponse));
    }

    public static void delIndex() throws IOException {
        AcknowledgedResponse response = client.indices().delete(new DeleteIndexRequest("user"), RequestOptions.DEFAULT);
        System.out.println(objectMapper.writeValueAsString(response));
    }

    public static void createDoc() throws IOException {
        IndexRequest indexRequest = new IndexRequest();
        User user = new User();
        user.setName("张三");
        user.setAge(18);
        user.setSex("男");
        String s = objectMapper.writeValueAsString(user);
        indexRequest.index("user").id("1001").source(s, XContentType.JSON);
        IndexResponse index = client.index(indexRequest, RequestOptions.DEFAULT);
        System.out.println(index);
    }

    public static void updateDoc() throws IOException {
        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.index("user").id("1001").doc(XContentType.JSON, "sex", "女");
        UpdateResponse update = client.update(updateRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(update));
    }

    public static void getDoc() throws IOException {
        GetRequest getRequest = new GetRequest();
        getRequest.index("user").id("1001");
        GetResponse documentFields = client.get(getRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(documentFields));
    }

    public static void deleteDoc() throws IOException {
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.index("user").id("1001");
        DeleteResponse delete = client.delete(deleteRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(delete));
    }

    public static void batchInsert() throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(new IndexRequest().index("user").id("1001").source(XContentType.JSON, "name", "张三","sex","男","age",30));
        bulkRequest.add(new IndexRequest().index("user").id("1002").source(XContentType.JSON, "name", "李四","sex","女","age",35));
        bulkRequest.add(new IndexRequest().index("user").id("1003").source(XContentType.JSON, "name", "王五1","sex","男","age",40));
        bulkRequest.add(new IndexRequest().index("user").id("1004").source(XContentType.JSON, "name", "王五2","sex","女","age",45));
        bulkRequest.add(new IndexRequest().index("user").id("1005").source(XContentType.JSON, "name", "王五3","sex","男","age",50));
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(bulk));
    }

    public static void batchDelete() throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        bulkRequest.add(new DeleteRequest().index("user").id("1001"));
        bulkRequest.add(new DeleteRequest().index("user").id("1002"));
        bulkRequest.add(new DeleteRequest().index("user").id("1003"));
        BulkResponse bulk = client.bulk(bulkRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(bulk));
    }

    public static void queryAll() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchRequest.source(searchSourceBuilder);

        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }


    public static void queryByCondition() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.termQuery("age",30));
        searchRequest.source(searchSourceBuilder);

        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }

    public static void queryByConditionWithPage() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(2).from(0);
        searchRequest.source(searchSourceBuilder);

        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }

    public static void queryByConditionWithPageAndSort() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }

    public static void queryByConditionWithPageAndSortAndInclude() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);
        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }

    public static void queryByConditionWithPageAndSortAndIncludeAndBool() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        searchSourceBuilder.query(boolQueryBuilder);
//        boolQueryBuilder.must(QueryBuilders.matchQuery("age",30));
//        boolQueryBuilder.must(QueryBuilders.matchQuery("sex","男"));
//        boolQueryBuilder.mustNot(QueryBuilders.matchQuery("sex","男"));
        boolQueryBuilder.must(QueryBuilders.boolQuery().should(QueryBuilders.matchQuery("age",30)).should(QueryBuilders.matchQuery("age",35)));
        boolQueryBuilder.must(QueryBuilders.matchQuery("sex","男"));

        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);
//        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }

    public static void queryByConditionWithPageAndSortAndIncludeAndBoolAndRange() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchAllQuery());
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("age").gte(40));
        searchSourceBuilder.query(boolQueryBuilder);

        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);
//        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }


    public static void queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndFuzzy() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.fuzzyQuery("name","王五").fuzziness(Fuzziness.TWO));
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("age").gte(40));
        searchSourceBuilder.query(boolQueryBuilder);

        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);
//        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            System.out.println(hit.getSourceAsString());
        }
    }



    public static void queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndHighLight() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
//        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
//        boolQueryBuilder.must(QueryBuilders.matchQuery("name","五"));
//        boolQueryBuilder.filter(QueryBuilders.rangeQuery("age").gte(40));
        searchSourceBuilder.query(QueryBuilders.matchQuery("name","五"));

        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("name").preTags("<font color='red'>").postTags("</font>");
        searchSourceBuilder.highlighter(highlightBuilder);

//        searchSourceBuilder.size(10).from(0);
//        searchSourceBuilder.sort("age", SortOrder.DESC);
//        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            // 获取高亮字段
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            HighlightField nameHighlightField = highlightFields.get("name");
            if (nameHighlightField != null) {
                // 获取高亮内容
                Text[] fragments = nameHighlightField.fragments();
                if (fragments != null && fragments.length > 0) {
                    System.out.println("高亮内容: " + fragments[0].toString());
                }
            }
            System.out.println("原始内容: " + hit.getSourceAsString());
        }
    }


    public static void queryByConditionWithPageAndSortAndIncludeAndBoolAndRangeAndHighLightAndAggr() throws IOException {
        SearchRequest searchRequest = new SearchRequest();
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        boolQueryBuilder.must(QueryBuilders.matchQuery("name","五"));
        boolQueryBuilder.filter(QueryBuilders.rangeQuery("age").gte(40));

        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("name").preTags("<font color='red'>").postTags("</font>");
        searchSourceBuilder.highlighter(highlightBuilder);

        MaxAggregationBuilder maxAge = AggregationBuilders.max("maxAge").field("age");
        searchSourceBuilder.aggregation(maxAge);

        TermsAggregationBuilder ageGroup = AggregationBuilders.terms("ageGroup").field("age");
        searchSourceBuilder.aggregation(ageGroup);

        searchSourceBuilder.size(10).from(0);
        searchSourceBuilder.sort("age", SortOrder.DESC);
//        searchSourceBuilder.fetchSource("name","");

        searchRequest.source(searchSourceBuilder);
        SearchResponse search = client.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println(writeValueAsString(search));
        for (SearchHit hit : search.getHits()) {
            // 获取高亮字段
            Map<String, HighlightField> highlightFields = hit.getHighlightFields();
            HighlightField nameHighlightField = highlightFields.get("name");
            if (nameHighlightField != null) {
                // 获取高亮内容
                Text[] fragments = nameHighlightField.fragments();
                if (fragments != null && fragments.length > 0) {
                    System.out.println("高亮内容: " + fragments[0].toString());
                }
            }
            System.out.println("原始内容: " + hit.getSourceAsString());
        }
        for (Aggregation aggregation : search.getAggregations()) {
            System.out.println(writeValueAsString(aggregation));
        }
    }
}