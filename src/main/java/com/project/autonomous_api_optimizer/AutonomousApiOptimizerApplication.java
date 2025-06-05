package com.project.autonomous_api_optimizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AutonomousApiOptimizerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutonomousApiOptimizerApplication.class, args);
	}

}
