package com.iot.config;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.IdleConnectionEvictor;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.security.KeyStore;

@Configuration
public class HttpClientConfig {
    @Value("${http.maxTotal}")
    private int maxTotal = 800;

    @Value("${http.defaultMaxPerRoute}")
    private int defaultMaxPerRoute = 80;

    @Value("${http.validateAfterInactivity}")
    private int validateAfterInactivity = 1000;

    @Value("${http.connectionRequestTimeout}")
    private int connectionRequestTimeout = 5000;

    @Value("${http.connectTimeout}")
    private int connectTimeout = 10000;

    @Value("${http.socketTimeout}")
    private int socketTimeout = 20000;

    @Value("${http.waitTime}")
    private long waitTime = 30000;

    @Bean
    public PoolingHttpClientConnectionManager createPoolingHttpClientConnectionManager() {
        PoolingHttpClientConnectionManager poolmanager = new PoolingHttpClientConnectionManager();
        poolmanager.setMaxTotal(maxTotal);
        poolmanager.setDefaultMaxPerRoute(defaultMaxPerRoute);
        poolmanager.setValidateAfterInactivity(validateAfterInactivity);
        return poolmanager;
    }

    @Bean
    public HttpClientBuilder createHttpClientBuilder(PoolingHttpClientConnectionManager poolManager) {
        return HttpClientBuilder.create().setConnectionManager(poolManager);
    }

    @Bean
    public CloseableHttpClient createHttpClient(HttpClientBuilder builder) {
        return builder.build();
    }

    @Bean
    public SSLContext createSSLContext() throws Exception {
        return SSLContexts.custom().loadTrustMaterial(KeyStore.getInstance(KeyStore.getDefaultType()), new TrustAllStrategy()).build();
    }

    @Bean
    public SSLConnectionSocketFactory createSSLConnectionSocketFactory(SSLContext sslContext) {
        return new SSLConnectionSocketFactory(sslContext,
                new String[] { "TLSv1" },
                null,
                SSLConnectionSocketFactory.getDefaultHostnameVerifier());
    }

    @Bean
    public RequestConfig createRequestConfig() {
        return RequestConfig.custom()
                .setConnectionRequestTimeout(connectionRequestTimeout)  // 从连接池中取连接的超时时间
                .setConnectTimeout(connectTimeout)                      // 连接超时时间
                .setSocketTimeout(socketTimeout)                        // 请求超时时间
                .build();
    }

    @Bean
    public IdleConnectionEvictor createIdleConnectionEvictor(PoolingHttpClientConnectionManager poolManager) {
        IdleConnectionEvictor idleConnectionEvictor = new IdleConnectionEvictor(poolManager, waitTime, null);
        return idleConnectionEvictor;
    }
}
