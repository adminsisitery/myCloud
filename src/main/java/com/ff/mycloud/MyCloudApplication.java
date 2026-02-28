package com.ff.mycloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@MapperScan("com.ff.mycloud.repositorys")
public class MyCloudApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyCloudApplication.class, args);
    }
}