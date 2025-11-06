package mav_intel.com.Intelligent_Crypto_User_Management.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(
                            "http://localhost:3000",
                            "http://localhost:3001",
                            "http://localhost:3002",
                            "http://127.0.0.1:3000",
                            "http://127.0.0.1:3001",
                            "http://127.0.0.1:3002",
                            "https://telegram-signals-tau.vercel.app",
                            "https://cryptomanager-beta.vercel.app",
                            "https://cryptomanager-ebon.vercel.app",
                            "https://cryptomanager-8a05sk1ap-maverick-intel-sl.vercel.app"
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
