package mav_intel.com.Intelligent_Crypto_User_Management.service;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.TradeManagementConfigDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeManagementConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
public class TradeManagementConfigService {

    @Autowired
    private TradeManagementConfigRepository configRepository;

    /**
     * Default configuration - used as fallback
     */
    private static final TradeManagementConfig DEFAULT_CONFIG = TradeManagementConfig.builder()
            .maxPositionSizePercent(new BigDecimal("10"))
            .maxLeverage(new BigDecimal("20"))
            .tp1Percentage(new BigDecimal("25"))
            .tp2Percentage(new BigDecimal("25"))
            .tp3Percentage(new BigDecimal("25"))
            .tp4Percentage(new BigDecimal("25"))
            .marginMode("ISOLATE")
            .build();

    /**
     * Get active configuration for user (from DB or defaults)
     */
    public TradeManagementConfig getActiveConfig(Long userId) {
        log.info("📊 Fetching trade management config for user: {}", userId);
        return configRepository.findByUserId(userId)
                .orElse(DEFAULT_CONFIG);
    }

    /**
     * Save or update configuration for user
     */
    public TradeManagementConfig saveConfig(Long userId, TradeManagementConfigDTO dto) {
        log.info("💾 Saving trade management config for user: {}", userId);

        // Validate TP percentages sum to 100
        validateTPPercentages(dto);

        // Validate margin mode
        validateMarginMode(dto);

        // Check if config exists
        TradeManagementConfig config = configRepository.findByUserId(userId)
                .orElse(new TradeManagementConfig());

        boolean isNew = config.getId() == null;
        if (isNew) {
            log.info("📝 Creating NEW config for user: {}", userId);
        } else {
            log.info("🔄 Updating existing config (ID: {}) for user: {}", config.getId(), userId);
        }

        config.setUserId(userId);
        config.setMaxPositionSize(dto.getMaxPositionSize());
        config.setMaxLeverage(dto.getMaxLeverage());
        config.setTp1ExitPercentage(dto.getTp1ExitPercentage());
        config.setTp2ExitPercentage(dto.getTp2ExitPercentage());
        config.setTp3ExitPercentage(dto.getTp3ExitPercentage());
        config.setTp4ExitPercentage(dto.getTp4ExitPercentage());
        config.setMarginMode(dto.getMarginMode());

        TradeManagementConfig saved = configRepository.save(config);
        log.info("✅ Configuration saved to DB for user {}: ConfigID={}, MaxPos={}, MaxLev={}, MarginMode={}, TP:{}%/{}%/{}%/{}%",
            userId,
            saved.getId(),
            saved.getMaxPositionSize(),
            saved.getMaxLeverage(),
            saved.getMarginMode(),
            saved.getTp1ExitPercentage(),
            saved.getTp2ExitPercentage(),
            saved.getTp3ExitPercentage(),
            saved.getTp4ExitPercentage()
        );
        return saved;
    }

    /**
     * Reset user's configuration to system defaults
     * Deletes the user's custom config, so they'll use defaults on next fetch
     */
    @Transactional
    public void resetConfig(Long userId) {
        log.info("🔄 Resetting trade management config for user: {}", userId);

        configRepository.deleteByUserId(userId);

        log.info("✅ Configuration reset to defaults for user: {}", userId);
    }

    /**
     * Validate if a trade request complies with user's configuration
     * Returns true if trade is allowed, false if it violates limits
     */
    public boolean isTradeValid(Long userId, ExecuteTradeRequest trade) {
        TradeManagementConfig config = getActiveConfig(userId);

        BigDecimal tradeAmount = BigDecimal.valueOf(trade.getAmount() != null ? trade.getAmount() : 0);
        BigDecimal tradeLeverage = BigDecimal.valueOf(trade.getLeverage() != null ? trade.getLeverage() : 1);

        // Check 1: Position Size
        if (tradeAmount.compareTo(config.getMaxPositionSize()) > 0) {
            log.warn("❌ Trade REJECTED for user {}: Position size ${} exceeds max ${}",
                    userId, tradeAmount, config.getMaxPositionSize());
            return false;
        }

        // Check 2: Leverage
        if (tradeLeverage.compareTo(config.getMaxLeverage()) > 0) {
            log.warn("❌ Trade REJECTED for user {}: Leverage {}x exceeds max {}x",
                    userId, tradeLeverage, config.getMaxLeverage());
            return false;
        }

        log.info("✅ Trade VALIDATED for user {}: Amount=${}, Leverage={}x",
                userId, tradeAmount, tradeLeverage);
        return true;
    }

    /**
     * Get validation error message for a failed trade
     */
    public String getValidationError(Long userId, ExecuteTradeRequest trade) {
        TradeManagementConfig config = getActiveConfig(userId);

        BigDecimal tradeAmount = BigDecimal.valueOf(trade.getAmount() != null ? trade.getAmount() : 0);
        BigDecimal tradeLeverage = BigDecimal.valueOf(trade.getLeverage() != null ? trade.getLeverage() : 1);

        if (tradeAmount.compareTo(config.getMaxPositionSize()) > 0) {
            return "Position size $" + tradeAmount + " exceeds your maximum of $" + config.getMaxPositionSize();
        }

        if (tradeLeverage.compareTo(config.getMaxLeverage()) > 0) {
            return "Leverage " + tradeLeverage + "x exceeds your maximum of " + config.getMaxLeverage() + "x";
        }

        return null;
    }

    /**
     * Private helper to validate TP percentages sum to 100
     */
    private void validateTPPercentages(TradeManagementConfigDTO dto) {
        BigDecimal total = dto.getTp1ExitPercentage()
                .add(dto.getTp2ExitPercentage())
                .add(dto.getTp3ExitPercentage())
                .add(dto.getTp4ExitPercentage());

        if (total.compareTo(new BigDecimal("100")) != 0) {
            log.error("❌ TP percentages don't sum to 100: {}", total);
            throw new IllegalArgumentException("Take Profit percentages must sum to 100%, got " + total);
        }
    }

    /**
     * Private helper to validate margin mode is either ISOLATE, ISOLATED, or CROSS
     */
    private void validateMarginMode(TradeManagementConfigDTO dto) {
        String marginMode = dto.getMarginMode();
        if (marginMode == null || (!marginMode.equals("ISOLATE") && !marginMode.equals("ISOLATED") && !marginMode.equals("CROSS"))) {
            log.error("❌ Invalid margin mode: {}", marginMode);
            throw new IllegalArgumentException("Margin mode must be either ISOLATE, ISOLATED, or CROSS, got: " + marginMode);
        }
    }
}
