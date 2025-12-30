package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.service.AutoTradeExecutor;
import mav_intel.com.Intelligent_Crypto_User_Management.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Webhook endpoint to receive trading signals from Python backend (Telegram message collector)
 * This controller listens for incoming signals and automatically executes trades
 */
@Slf4j
@RestController
@RequestMapping("/api/webhook")
public class SignalWebhookController {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private AutoTradeExecutor autoTradeExecutor;

    @Autowired
    private SignalRepository signalRepository;

    @Value("${trade.auto-execute:true}")
    private boolean autoExecuteEnabled;

    /**
     * Receive signal from Python Telegram message collector and execute trade
     *
     * Expected payload from Python backend:
     * {
     *   "pair": "ARBUSDT.P",
     *   "setup_type": "LONG",
     *   "entry": 1.098,
     *   "leverage": 20,
     *   "tp1": 1.112,
     *   "tp2": 1.128,
     *   "tp3": 1.145,
     *   "tp4": 1.165,
     *   "stop_loss": 1.075,
     *   "timestamp": "2025-11-02T12:30:00Z",
     *   "full_message": "...",
     * }
     */
    @PostMapping("/signal")
    public ResponseEntity<?> receiveSignal(@RequestBody Map<String, Object> signalData) {
        try {
            log.info("📡 Received signal from Python backend: {}", signalData.get("pair"));

            // 1. Check for duplicate signal (same pair, entry, setup_type within last 60 seconds)
            Signal signal = mapToSignal(signalData);
            if (isDuplicateSignal(signal)) {
                log.warn("⚠️ Duplicate signal detected and rejected: {} {} @ {}",
                    signal.getPair(), signal.getSetupType(), signal.getEntry());
                Map<String, Object> response = new HashMap<>();
                response.put("status", "duplicate");
                response.put("message", "Duplicate signal rejected");
                return ResponseEntity.ok(response);
            }

            // 2. Save signal to database
            Signal savedSignal = signalRepository.save(signal);
            log.info("✅ Signal saved to database with ID: {}", savedSignal.getId());

            // 2. Auto-execute trade if enabled
            if (autoExecuteEnabled) {
                ExecuteTradeRequest tradeRequest = mapSignalToTradeRequest(signalData, savedSignal.getId());
                ExecuteTradeResponse tradeResponse = tradeService.executeTrade(tradeRequest);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Signal received and trade executed");
                response.put("signal_id", savedSignal.getId());
                response.put("trade_status", tradeResponse.getStatus());
                response.put("trade_id", tradeResponse.getTradeId());
                response.put("trade_message", tradeResponse.getMessage());

                log.info("🎯 Trade auto-executed for signal {}", savedSignal.getId());
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️ Auto-execution disabled. Signal saved but trade not executed");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Signal received but auto-execution is disabled");
                response.put("signal_id", savedSignal.getId());
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("❌ Error processing signal webhook: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process signal: " + e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Health check endpoint for webhook
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("auto_execute_enabled", autoExecuteEnabled);
        status.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(status);
    }

    /**
     * Check if signal is a duplicate (same pair, entry, setup_type within last 60 seconds)
     */
    private boolean isDuplicateSignal(Signal signal) {
        try {
            // Look for signals with same pair, entry price, and setup type in the last 60 seconds
            OffsetDateTime sixtySecondsAgo = OffsetDateTime.now().minusSeconds(60);

            // Query for existing signals
            var existingSignals = signalRepository.findByPairAndSetupTypeAndEntry(
                signal.getPair(),
                signal.getSetupType(),
                signal.getEntry()
            );

            // Check if any match within the time window
            for (Signal existing : existingSignals) {
                if (existing.getTimestamp() != null &&
                    existing.getTimestamp().isAfter(sixtySecondsAgo)) {
                    log.debug("Found duplicate signal ID {} from {} seconds ago",
                        existing.getId(),
                        java.time.Duration.between(existing.getTimestamp(), OffsetDateTime.now()).getSeconds());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error checking for duplicate signal: {}", e.getMessage());
            return false; // If check fails, allow signal through
        }
    }

    /**
     * Map webhook signal data to Signal entity
     */
    private Signal mapToSignal(Map<String, Object> data) {
        Signal signal = new Signal();
        signal.setPair(getString(data, "pair"));
        signal.setSetupType(getString(data, "setup_type"));
        signal.setEntry(getDouble(data, "entry"));
        signal.setTakeProfit(getDouble(data, "take_profit")); // Single TP calculated by Python
        signal.setStopLoss(getDouble(data, "stop_loss"));
        signal.setFullMessage(getString(data, "full_message"));
        signal.setChannel("TELEGRAM");
        signal.setTimestamp(OffsetDateTime.now());
        return signal;
    }

    /**
     * Map signal data to ExecuteTradeRequest
     */
    private ExecuteTradeRequest mapSignalToTradeRequest(Map<String, Object> data, Long signalId) {
        ExecuteTradeRequest request = new ExecuteTradeRequest();

        // Use LONG/SHORT directly (matching Signal format)
        String setupType = getString(data, "setup_type");
        request.setSide(setupType); // LONG or SHORT

        request.setPair(getString(data, "pair"));
        request.setEntry(getDouble(data, "entry"));
        request.setLeverage(20); // Default 20x leverage
        request.setTakeProfit(getDouble(data, "take_profit")); // Single TP calculated by Python
        request.setStopLoss(getDouble(data, "stop_loss"));
        request.setQuantity(null); // Auto-calculated by TradeService
        request.setSignalId(signalId);

        return request;
    }

    /**
     * Helper to safely get String from map
     */
    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Helper to safely get Double from map
     */
    private Double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
