package com.example.kindleboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 单进程提供管理页、文件存储与灰度图片，不依赖 Kindle 客户端。 */
@SpringBootApplication
@EnableScheduling
public class KindleBoardApplication {
    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        SpringApplication.run(KindleBoardApplication.class, args);
    }
}
