package mav_intel.com.Intelligent_Crypto_User_Management.config;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BinanceConfig {

    @Value("${binance.api.key:}")
    private String apiKey;

    @Value("${binance.api.secret:}")
    private String apiSecret;

    @Value("${binance.testnet.enabled:false}")
    private boolean testnetEnabled;

    @Bean
    public UMFuturesClientImpl umFuturesClient() {
        String key = apiKey;
        String secret = apiSecret;

        // Check environment variables if properties are empty
        if (key == null || key.isEmpty()) {
            key = System.getenv("BINANCE_API_KEY");
        }
        if (secret == null || secret.isEmpty()) {
            secret = System.getenv("BINANCE_API_SECRET");
        }

        if (key == null || key.isEmpty() || secret == null || secret.isEmpty()) {
            log.warn("⚠️ Binance API keys not configured. Please set BINANCE_API_KEY and BINANCE_API_SECRET environment variables");
            return null;
        }

        if (testnetEnabled) {
            log.info("✅ Binance Futures client initialized in TESTNET mode");
            return new UMFuturesClientImpl(key, secret, "https://testnet.binancefuture.com");
        } else {
            log.info("✅ Binance Futures client initialized in LIVE mode");
            return new UMFuturesClientImpl(key, secret);
        }
    }
}
