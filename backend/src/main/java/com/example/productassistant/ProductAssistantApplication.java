package com.example.productassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.example.productassistant")
public class ProductAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductAssistantApplication.class, args);
    }
}

