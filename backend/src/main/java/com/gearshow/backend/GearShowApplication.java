package com.gearshow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GearShowApplication {

	public static void main(String[] args) {
		SpringApplication.run(GearShowApplication.class, args);
	}

}
