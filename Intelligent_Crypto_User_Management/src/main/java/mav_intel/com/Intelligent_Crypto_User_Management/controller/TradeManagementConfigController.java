package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.TradeManagementConfigRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeManagementConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/trade-management-config")
@CrossOrigin(originPatterns = "*", allowCredentials = "false")
public class TradeManagementConfigController {

    @Autowired
    private TradeManagementConfigRepository configRepository;

    /**
     * Get trade management config for a user (default: userId=1)
     */
    @GetMapping
    public ResponseEntity<TradeManagementConfig> getConfig(@RequestParam(required = false, defaultValue = "1") Long userId) {
        log.info("📋 Getting trade management config for user: {}", userId);

        Optional<TradeManagementConfig> config = configRepository.findByUserId(userId);

        if (config.isPresent()) {
            return ResponseEntity.ok(config.get());
        } else {
            // Return default config
            TradeManagementConfig defaultConfig = new TradeManagementConfig();
            defaultConfig.setUserId(userId);
            return ResponseEntity.ok(defaultConfig);
        }
    }

    /**
     * Save/Update trade management config
     */
    @PostMapping
    public ResponseEntity<TradeManagementConfig> saveConfig(@RequestBody TradeManagementConfigRequest request) {
        log.info("💾 Saving trade management config: {}", request);

        Long userId = request.getUserId() != null ? request.getUserId() : 1L;

        Optional<TradeManagementConfig> existingConfig = configRepository.findByUserId(userId);

        TradeManagementConfig config;
        if (existingConfig.isPresent()) {
            config = existingConfig.get();
            log.info("✏️ Updating existing config for user: {}", userId);
        } else {
            config = new TradeManagementConfig();
            config.setUserId(userId);
            log.info("🆕 Creating new config for user: {}", userId);
        }

        // Update only the fields from the request
        if (request.getMarginMode() != null) {
            config.setMarginMode(request.getMarginMode());
        }
        if (request.getMaxLeverage() != null) {
            config.setMaxLeverage(request.getMaxLeverage());
        }
        if (request.getMaxPositionSizePercent() != null) {
            config.setMaxPositionSizePercent(request.getMaxPositionSizePercent());
        }

        TradeManagementConfig savedConfig = configRepository.save(config);
        log.info("✅ Config saved successfully: ID={}", savedConfig.getId());

        return ResponseEntity.ok(savedConfig);
    }
}
