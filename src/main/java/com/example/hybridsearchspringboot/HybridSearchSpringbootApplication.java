package com.example.hybridsearchspringboot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class HybridSearchSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(HybridSearchSpringbootApplication.class, args);
        log.error("--------------------启动成功--------------------");
    }

}
