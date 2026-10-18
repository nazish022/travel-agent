package com.substring.agent.backend.ai_agent_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.substring.agent.backend")
public class AiAgentBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiAgentBackendApplication.class, args);
	}

}
