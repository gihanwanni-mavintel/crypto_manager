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
                // ✅ FIXED: Support ANY Vercel preview URL using allowedOriginPatterns()
                // This uses regex to match *.vercel.app and localhost:*
                registry.addMapping("/**")
                        .allowedOriginPatterns(
                            "http://localhost:*",              // Local development on any port
                            "http://127.0.0.1:*",              // Localhost IP on any port
                            "https://.*\\.vercel\\.app"        // ANY Vercel domain (regex: *.vercel.app)
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}
