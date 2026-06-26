package com.example.badukanalyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BadukAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(BadukAnalyzerApplication.class, args);
    }

}
