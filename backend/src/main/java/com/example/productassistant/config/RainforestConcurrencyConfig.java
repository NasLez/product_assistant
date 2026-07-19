package com.example.productassistant.config;

import java.util.concurrent.Semaphore;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RainforestConcurrencyConfig {

    @Bean("rainforestSemaphore")
    public Semaphore rainforestSemaphore() {
        return new Semaphore(1, true);
    }
}

