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
import java.net.InetAddress;
import java.net.UnknownHostException;

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
     *   "user_id": 1,              ✅ REQUIRED - User identifier
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

            // ✅ VALIDATE userId is provided
            Long userId = getLong(signalData, "user_id");
            if (userId == null || userId <= 0) {
                log.error("❌ Missing or invalid user_id in signal payload");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", "Missing or invalid user_id in signal payload");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            log.info("✅ Signal for user_id: {}", userId);

            // 1. Save signal to database
            Signal signal = mapToSignal(signalData, userId);
            Signal savedSignal = signalRepository.save(signal);
            log.info("✅ Signal saved to database with ID: {} for user {}", savedSignal.getId(), userId);

            // 2. Auto-execute trade if enabled
            if (autoExecuteEnabled) {
                ExecuteTradeRequest tradeRequest = mapSignalToTradeRequest(signalData, savedSignal.getId(), userId);
                ExecuteTradeResponse tradeResponse = tradeService.executeTrade(tradeRequest);

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Signal received and trade executed");
                response.put("signal_id", savedSignal.getId());
                response.put("trade_status", tradeResponse.getStatus());
                response.put("trade_id", tradeResponse.getTradeId());
                response.put("trade_message", tradeResponse.getMessage());

                log.info("🎯 Trade auto-executed for signal {} (user {})", savedSignal.getId(), userId);
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
     * Also returns server IP for Binance API whitelisting
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "healthy");
        status.put("auto_execute_enabled", autoExecuteEnabled);
        status.put("timestamp", System.currentTimeMillis());

        // 🔍 Get server IP for Binance API whitelisting
        try {
            String serverIp = InetAddress.getLocalHost().getHostAddress();
            String hostname = InetAddress.getLocalHost().getHostName();
            status.put("server_ip", serverIp);
            status.put("server_hostname", hostname);
            log.info("🌐 Server IP: {} ({})", serverIp, hostname);
        } catch (UnknownHostException e) {
            log.warn("⚠️ Could not determine server IP: {}", e.getMessage());
            status.put("server_ip", "UNKNOWN");
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Map webhook signal data to Signal entity
     */
    private Signal mapToSignal(Map<String, Object> data, Long userId) {
        Signal signal = new Signal();
        signal.setPair(getString(data, "pair"));
        signal.setSetupType(getString(data, "setup_type"));
        signal.setEntry(getDouble(data, "entry"));
        // Convert Double leverage to Integer
        Double leverageDouble = getDouble(data, "leverage");
        signal.setLeverage(leverageDouble != null ? leverageDouble.intValue() : 1);
        signal.setTp1(getDouble(data, "tp1"));
        signal.setTp2(getDouble(data, "tp2"));
        signal.setTp3(getDouble(data, "tp3"));
        signal.setTp4(getDouble(data, "tp4"));
        signal.setStopLoss(getDouble(data, "stop_loss"));
        signal.setFullMessage(getString(data, "full_message"));
        signal.setChannel("TELEGRAM");
        signal.setTimestamp(OffsetDateTime.now());
        signal.setQuantity(getDouble(data, "quantity"));
        signal.setUserId(userId); // ✅ Set userId
        return signal;
    }

    /**
     * Map signal data to ExecuteTradeRequest
     * ✅ ADDED: Comprehensive logging to track value flow from Python to Java
     */
    private ExecuteTradeRequest mapSignalToTradeRequest(Map<String, Object> data, Long signalId, Long userId) {
        ExecuteTradeRequest request = new ExecuteTradeRequest();

        // Convert LONG/SHORT to BUY/SELL
        String setupType = getString(data, "setup_type");
        request.setSide(setupType.equalsIgnoreCase("LONG") ? "BUY" : "SELL");

        request.setPair(getString(data, "pair"));

        // ✅ LOG ENTRY VALUE
        Double entryValue = getDouble(data, "entry");
        log.info("🔍 [VALUE TRACE] Entry from Python: {} (type: {})", entryValue, data.get("entry") != null ? data.get("entry").getClass().getSimpleName() : "null");
        request.setEntry(entryValue);

        // Convert Double leverage to Integer safely
        Double leverageDouble = getDouble(data, "leverage");
        request.setLeverage(leverageDouble != null ? leverageDouble.intValue() : 1);

        // ✅ LOG TP VALUES
        Double tp1Value = getDouble(data, "tp1");
        Double tp2Value = getDouble(data, "tp2");
        Double tp3Value = getDouble(data, "tp3");
        Double tp4Value = getDouble(data, "tp4");
        log.info("🔍 [VALUE TRACE] TP values from Python: TP1={}, TP2={}, TP3={}, TP4={}", tp1Value, tp2Value, tp3Value, tp4Value);
        request.setTp1(tp1Value);
        request.setTp2(tp2Value);
        request.setTp3(tp3Value);
        request.setTp4(tp4Value);

        // ✅ LOG STOP LOSS VALUE
        Double slValue = getDouble(data, "stop_loss");
        log.info("🔍 [VALUE TRACE] Stop Loss from Python: {} (type: {})", slValue, data.get("stop_loss") != null ? data.get("stop_loss").getClass().getSimpleName() : "null");
        request.setStopLoss(slValue);

        request.setQuantity(getDouble(data, "quantity")); // Can be null, auto-calculated
        request.setSignalId(signalId);
        request.setUserId(userId); // ✅ Set userId

        // ✅ LOG FINAL REQUEST VALUES
        log.info("✅ [TRACE SUMMARY] ExecuteTradeRequest created:");
        log.info("   Entry: {}, TP1: {}, TP2: {}, TP3: {}, TP4: {}, SL: {}",
            request.getEntry(), request.getTp1(), request.getTp2(), request.getTp3(), request.getTp4(), request.getStopLoss());

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

    /**
     * Helper to safely get Long from map
     */
    private Long getLong(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
