package com.scalper.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;

import java.time.Duration;

/**
 * Configuration RestTemplate pour API cTrader
 */
@Configuration
public class RestTemplateConfig {

    @Value("${ctrader.rest.connection-pool-size:20}")
    private int connectionPoolSize;

    @Value("${ctrader.rest.max-connections-per-route:10}")
    private int maxConnectionsPerRoute;

    @Value("${scalper.broker.ctrader.connection-timeout:30000}")
    private int connectionTimeout;

    @Value("${scalper.broker.ctrader.read-timeout:60000}")
    private int readTimeout;

    @Bean
    public RestTemplate restTemplate() {
        // Configuration connection pooling
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(connectionPoolSize);
        connectionManager.setDefaultMaxPerRoute(maxConnectionsPerRoute);

        // Client HTTP avec timeouts
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .build();

        // Request factory avec timeouts
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setConnectTimeout(connectionTimeout);

        return new RestTemplate(requestFactory);
    }
}