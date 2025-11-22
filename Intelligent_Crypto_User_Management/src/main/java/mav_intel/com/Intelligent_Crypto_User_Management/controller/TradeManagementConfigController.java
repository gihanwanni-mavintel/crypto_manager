package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.TradeManagementConfigDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import mav_intel.com.Intelligent_Crypto_User_Management.model.User;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.UserRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.service.TradeManagementConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/config")
public class TradeManagementConfigController {

    @Autowired
    private TradeManagementConfigService configService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Get current trade management configuration for authenticated user
     * GET /api/config/trade-settings
     */
    @GetMapping("/trade-settings")
    public ResponseEntity<?> getTradeSettings() {
        try {
            Long userId = getCurrentUserId();
            log.info("📊 Fetching trade settings for user ID: {}", userId);

            TradeManagementConfig config = configService.getActiveConfig(userId);

            if (config != null && config.getId() != null) {
                log.info("✅ Found config from DB for user {}: maxPos={}, maxLev={}",
                    userId, config.getMaxPositionSize(), config.getMaxLeverage());
            } else {
                log.info("ℹ️ Using default config for user: {} (no saved config found)", userId);
            }

            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("❌ Error fetching trade settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error fetching trade settings: " + e.getMessage());
        }
    }

    /**
     * Reset trade management configuration to system defaults
     * DELETE /api/config/trade-settings
     */
    @DeleteMapping("/trade-settings")
    public ResponseEntity<?> resetTradeSettings() {
        log.info("🔄 DELETE /api/config/trade-settings endpoint called");
        try {
            Long userId = getCurrentUserId();
            log.info("🔄 Resetting trade settings for user ID: {}", userId);

            configService.resetConfig(userId);

            log.info("✅ Trade settings RESET to defaults for user: {}", userId);
            return ResponseEntity.ok(new java.util.HashMap<String, String>() {{
                put("message", "Configuration reset to system defaults");
                put("status", "success");
            }});
        } catch (Exception e) {
            log.error("❌ Error resetting trade settings", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error resetting trade settings: " + e.getMessage());
        }
    }

    /**
     * Save/update trade management configuration for authenticated user
     * POST /api/config/trade-settings
     */
    @PostMapping("/trade-settings")
    public ResponseEntity<?> saveTradeSettings(@RequestBody TradeManagementConfigDTO dto) {
        try {
            Long userId = getCurrentUserId();
            log.info("💾 Saving trade settings for user ID: {} | MaxPos={}, MaxLev={}, TP:{}%/{}%/{}%/{}%",
                userId, dto.getMaxPositionSize(), dto.getMaxLeverage(),
                dto.getTp1ExitPercentage(), dto.getTp2ExitPercentage(),
                dto.getTp3ExitPercentage(), dto.getTp4ExitPercentage());

            // Validate input
            if (dto.getMaxPositionSize() == null || dto.getMaxPositionSize().signum() <= 0) {
                log.warn("⚠️ Invalid position size: {}", dto.getMaxPositionSize());
                return ResponseEntity.badRequest()
                        .body("Max position size must be greater than 0");
            }
            if (dto.getMaxLeverage() == null || dto.getMaxLeverage().signum() <= 0) {
                log.warn("⚠️ Invalid leverage: {}", dto.getMaxLeverage());
                return ResponseEntity.badRequest()
                        .body("Max leverage must be greater than 0");
            }

            TradeManagementConfig config = configService.saveConfig(userId, dto);
            log.info("✅ Trade settings SAVED to DB for user {}: ID={}, MaxPos={}, MaxLev={}",
                userId, config.getId(), config.getMaxPositionSize(), config.getMaxLeverage());
            return ResponseEntity.ok(config);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error saving trade settings", e);
            e.printStackTrace(); // Print full stack trace for debugging
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving trade settings: " + e.getMessage());
        }
    }

    /**
     * Helper method to extract userId from JWT token
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("User not authenticated");
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        return user.getId();
    }
}
