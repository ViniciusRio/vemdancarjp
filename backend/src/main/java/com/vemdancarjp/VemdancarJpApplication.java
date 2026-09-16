package com.vemdancarjp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class VemdancarJpApplication {

    public static void main(String[] args) {
        SpringApplication.run(VemdancarJpApplication.class, args);
    }
}
