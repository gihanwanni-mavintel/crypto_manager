package mav_intel.com.Intelligent_Crypto_User_Management;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class IntelligentCryptoUserManagementApplication {

	public static void main(String[] args) {
		// Load .env file and set environment variables
		Dotenv dotenv = Dotenv.configure()
				.directory(".")
				.ignoreIfMissing()
				.load();

		// Set system properties from .env file
		dotenv.entries().forEach(entry ->
			System.setProperty(entry.getKey(), entry.getValue())
		);

		SpringApplication.run(IntelligentCryptoUserManagementApplication.class, args);
	}

}
