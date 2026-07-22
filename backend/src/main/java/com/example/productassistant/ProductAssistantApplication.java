package com.example.productassistant;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "com.example.productassistant", annotationClass = Mapper.class)
@EnableScheduling
public class ProductAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductAssistantApplication.class, args);
    }
}
