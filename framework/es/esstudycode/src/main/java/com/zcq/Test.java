package com.zcq;

import lombok.SneakyThrows;
import org.apache.http.HttpHost;
import org.elasticsearch.client.NodeSelector;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.sniff.SniffOnFailureListener;
import org.elasticsearch.client.sniff.Sniffer;

public class Test {

    @SneakyThrows
    public static void main(String[] args) {

        SniffOnFailureListener sniffOnFailureListener = new SniffOnFailureListener();

        //region 初始化
        RestClientBuilder builder = RestClient.builder(new HttpHost("10.1.24.135", 9200, "http"))
                .setFailureListener(sniffOnFailureListener);

        RestHighLevelClient highLevelClient = new RestHighLevelClient(builder);

        Sniffer sniffer = Sniffer.builder(highLevelClient.getLowLevelClient()).setSniffIntervalMillis(5000)
                .setSniffAfterFailureDelayMillis(30000)
                .build();

        sniffOnFailureListener.setSniffer(sniffer);

        //endregion

        sniffer.close();
        highLevelClient.close();
    }
}
