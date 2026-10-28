package com.example.sikka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class SikkaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SikkaApplication.class, args);
    }

}
