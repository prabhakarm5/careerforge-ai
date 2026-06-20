package com.trackai.backend;

import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootApplication
@RequiredArgsConstructor
public class BackendApplication {

	private static Logger logger = LoggerFactory.getLogger(BackendApplication.class);

	public static void main(String[] args) {

		SpringApplication.run(
				BackendApplication.class,
				args);

		logger.info("Server Started");
	}

	@Bean
	CommandLineRunner createAdminUser(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder) {

		return args -> {

			String adminEmail = "prabhakarm725@gmail.com";

			// CHECK ADMIN EXISTS
			if (userRepository
					.findByEmail(adminEmail)
					.isEmpty()) {

				User admin = User.builder()

						.id(UUID.randomUUID().toString())

						.name("Super Admin")

						.email(adminEmail)

						.password(
								passwordEncoder.encode(
										"admin123"))

						.blocked(false)

						.enabled(true)

						.emailVerified(true)

						.role(Role.ROLE_ADMIN)

						.createdAt(LocalDateTime.now())
						.mobileNumber("9026106605")

						.build();

				userRepository.save(admin);

				logger.info("Admin user created successfully");

			} else {

				logger.info(
						"Admin already exists");
			}
		};
	}
}