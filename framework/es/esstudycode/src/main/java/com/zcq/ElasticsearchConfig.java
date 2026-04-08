package com.zcq;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.Duration;

/**
 * Elasticsearch 客户端配置
 *
 * 注意：生产环境请使用正规 CA 证书，不要忽略 SSL 验证！
 * 此处仅适用于开发/测试阶段连接使用自签名证书的 ES 节点。
 */
@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo("10.1.24.136:9200")
                .usingSsl(buildUnsafeSslContext())   // 跳过自签名证书验证
                .withBasicAuth("elastic", "6081O0VdwHJF-SdsLNTy")
                .withConnectTimeout(Duration.ofSeconds(5))
                .withSocketTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 构造一个信任所有证书的 SSLContext（仅限开发环境）
     */
    private SSLContext buildUnsafeSslContext() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("Failed to create unsafe SSL context", e);
        }
    }
}