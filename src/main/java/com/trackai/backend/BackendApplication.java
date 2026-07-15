package com.trackai.backend;

import com.trackai.backend.entity.User;
import com.trackai.backend.enums.Role;
import com.trackai.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootApplication
@RequiredArgsConstructor
@EnableAsync
@EnableScheduling
public class BackendApplication {

	private static Logger logger = LoggerFactory.getLogger(BackendApplication.class);

	// ✅ NEW: hardcoded values hata ke application.yml se pick ho rahe hain,
	// jo aage se .env ke ADMIN_EMAIL / ADMIN_PASSWORD / ADMIN_MOBILE se aayenge.
	@Value("${app.default-admin.email}")
	private String adminEmail;

	@Value("${app.default-admin.password}")
	private String adminPassword;

	@Value("${app.default-admin.mobile}")
	private String adminMobile;

	@Value("${app.default-admin.name}")
	private String adminName;

	public static void main(String[] args) {

		SpringApplication.run(
				BackendApplication.class,
				args);

		logger.info("Server Started!!!!!");
	}

	@Bean
	CommandLineRunner createAdminUser(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder) {

		return args -> {

			// CHECK ADMIN EXISTS
			if (userRepository
					.findByEmail(adminEmail)
					.isEmpty()) {

				User admin = User.builder()

						.id(UUID.randomUUID().toString())

						.name(adminName)

						.email(adminEmail)

						.password(
								passwordEncoder.encode(
										adminPassword))

						.blocked(false)

						.enabled(true)

						.emailVerified(true)

						.role(Role.ROLE_ADMIN)

						.createdAt(LocalDateTime.now())
						.mobileNumber(adminMobile)

						.build();

				userRepository.save(admin);

				logger.info("Admin user created successfully");

			} else {

				logger.info(
						"Admin already exists");
				logger.info(" Admin Email: " + adminEmail);
			}
		};
	}
}