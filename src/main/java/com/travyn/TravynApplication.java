package com.travyn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TravynApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravynApplication.class, args);
    }
}
