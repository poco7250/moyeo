package com.poco7250.moyeo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MoyeoApplication {

	public static void main(String[] args) {
		SpringApplication.run(MoyeoApplication.class, args);
	}

}
