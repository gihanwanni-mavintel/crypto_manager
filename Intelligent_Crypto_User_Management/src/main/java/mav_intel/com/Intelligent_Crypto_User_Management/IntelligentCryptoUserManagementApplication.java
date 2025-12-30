package mav_intel.com.Intelligent_Crypto_User_Management;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelligentCryptoUserManagementApplication {

	public static void main(String[] args) {
		// Load .env file and set environment variables
		try {
			Dotenv dotenv = Dotenv.configure()
					.directory(System.getProperty("user.dir"))
					.ignoreIfMissing()
					.load();

			// Set system properties from .env file
			dotenv.entries().forEach(entry ->
				System.setProperty(entry.getKey(), entry.getValue())
			);

			System.out.println("[OK] Loaded .env file from: " + System.getProperty("user.dir"));
		} catch (Exception e) {
			System.out.println("[WARN] Could not load .env file: " + e.getMessage());
			System.out.println("[INFO] Using environment variables or application.properties defaults");
		}

		SpringApplication.run(IntelligentCryptoUserManagementApplication.class, args);
	}

}
