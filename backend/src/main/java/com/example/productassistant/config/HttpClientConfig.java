package com.example.productassistant.config;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientConfig {

    @Bean("rainforestWebClient")
    public WebClient rainforestWebClient(AppProperties properties, WebClient.Builder builder) {
        AppProperties.Rainforest config = properties.getRainforest();
        return builder.clone()
                .baseUrl(config.getBaseUrl())
                .clientConnector(connector(config.getConnectTimeout(), config.getReadTimeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Bean("deepSeekWebClient")
    public WebClient deepSeekWebClient(AppProperties properties, WebClient.Builder builder) {
        AppProperties.DeepSeek config = properties.getDeepseek();
        return builder.clone()
                .baseUrl(config.getBaseUrl())
                .clientConnector(connector(config.getConnectTimeout(), config.getReadTimeout()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .build();
    }

    private ReactorClientHttpConnector connector(Duration connectTimeout, Duration readTimeout) {
        HttpClient client = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(readTimeout)
                .doOnConnected(connection -> connection
                        .addHandlerLast(new ReadTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(readTimeout.toMillis(), TimeUnit.MILLISECONDS)));
        return new ReactorClientHttpConnector(client);
    }
}
