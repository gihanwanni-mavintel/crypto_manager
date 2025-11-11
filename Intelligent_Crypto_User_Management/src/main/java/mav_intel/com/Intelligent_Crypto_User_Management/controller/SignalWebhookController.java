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
            log.info("═══════════════════════════════════════════════════════════════════");
            log.info("📡 [WEBHOOK] Received signal from Python backend");
            log.info("📋 [WEBHOOK] Full payload: {}", signalData);

            String pair = getString(signalData, "pair");
            String setupType = getString(signalData, "setup_type");
            Double entry = getDouble(signalData, "entry");
            Double leverage = getDouble(signalData, "leverage");

            log.info("📊 [WEBHOOK] Signal details:");
            log.info("  - Pair: {}", pair);
            log.info("  - Setup Type: {}", setupType);
            log.info("  - Entry: {}", entry);
            log.info("  - Leverage: {}", leverage);
            log.info("  - TP1: {}", getDouble(signalData, "tp1"));
            log.info("  - TP2: {}", getDouble(signalData, "tp2"));
            log.info("  - TP3: {}", getDouble(signalData, "tp3"));
            log.info("  - TP4: {}", getDouble(signalData, "tp4"));
            log.info("  - Stop Loss: {}", getDouble(signalData, "stop_loss"));

            // 1. Map and save signal to database
            log.info("💾 [DATABASE] Mapping signal data to entity...");
            Signal signal = mapToSignal(signalData);
            log.info("💾 [DATABASE] Signal mapped successfully. Saving to database...");

            Signal savedSignal = signalRepository.save(signal);
            log.info("✅ [DATABASE] Signal saved successfully with ID: {}", savedSignal.getId());
            log.info("✅ [DATABASE] Signal entity: Pair={}, SetupType={}, Entry={}, Leverage={}",
                savedSignal.getPair(), savedSignal.getSetupType(), savedSignal.getEntry(), savedSignal.getLeverage());

            // 2. Auto-execute trade if enabled
            log.info("⚙️  [CONFIG] Auto-execution enabled: {}", autoExecuteEnabled);

            if (autoExecuteEnabled) {
                log.info("🚀 [TRADE] Creating trade request from signal {}...", savedSignal.getId());
                ExecuteTradeRequest tradeRequest = mapSignalToTradeRequest(signalData, savedSignal.getId());

                log.info("🚀 [TRADE] Trade request created:");
                log.info("  - Side: {}", tradeRequest.getSide());
                log.info("  - Pair: {}", tradeRequest.getPair());
                log.info("  - Entry: {}", tradeRequest.getEntry());
                log.info("  - Leverage: {}", tradeRequest.getLeverage());
                log.info("  - Quantity: {}", tradeRequest.getQuantity());

                log.info("🚀 [TRADE] Executing trade...");
                ExecuteTradeResponse tradeResponse = tradeService.executeTrade(tradeRequest);

                log.info("🚀 [TRADE] Trade execution response received:");
                log.info("  - Status: {}", tradeResponse.getStatus());
                log.info("  - Trade ID: {}", tradeResponse.getTradeId());
                log.info("  - Message: {}", tradeResponse.getMessage());

                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Signal received and trade executed");
                response.put("signal_id", savedSignal.getId());
                response.put("trade_status", tradeResponse.getStatus());
                response.put("trade_id", tradeResponse.getTradeId());
                response.put("trade_message", tradeResponse.getMessage());

                log.info("✅ [WEBHOOK] Trade auto-executed successfully for signal {}", savedSignal.getId());
                log.info("═══════════════════════════════════════════════════════════════════");
                return ResponseEntity.ok(response);
            } else {
                log.warn("⚠️  [CONFIG] Auto-execution is DISABLED. Signal saved but trade NOT executed");
                Map<String, Object> response = new HashMap<>();
                response.put("status", "success");
                response.put("message", "Signal received but auto-execution is disabled");
                response.put("signal_id", savedSignal.getId());
                log.info("═══════════════════════════════════════════════════════════════════");
                return ResponseEntity.ok(response);
            }

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════════════");
            log.error("❌ [ERROR] Exception while processing signal webhook");
            log.error("❌ [ERROR] Exception type: {}", e.getClass().getName());
            log.error("❌ [ERROR] Exception message: {}", e.getMessage());
            log.error("❌ [ERROR] Full exception: ", e);
            log.error("═══════════════════════════════════════════════════════════════════");

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Failed to process signal: " + e.getMessage());
            errorResponse.put("error_type", e.getClass().getSimpleName());
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
     * Map webhook signal data to Signal entity
     */
    private Signal mapToSignal(Map<String, Object> data) {
        log.debug("🔄 [MAPPING] Starting signal entity mapping...");

        Signal signal = new Signal();

        String pair = getString(data, "pair");
        signal.setPair(pair);
        log.debug("🔄 [MAPPING] Pair extracted: {}", pair);

        String setupType = getString(data, "setup_type");
        signal.setSetupType(setupType);
        log.debug("🔄 [MAPPING] Setup type extracted: {}", setupType);

        Double entry = getDouble(data, "entry");
        signal.setEntry(entry);
        log.debug("🔄 [MAPPING] Entry extracted: {}", entry);

        // Convert Double leverage to Integer
        Double leverageDouble = getDouble(data, "leverage");
        Integer leverage = leverageDouble != null ? leverageDouble.intValue() : 1;
        signal.setLeverage(leverage);
        log.debug("🔄 [MAPPING] Leverage extracted: {} (raw: {})", leverage, leverageDouble);

        Double tp1 = getDouble(data, "tp1");
        signal.setTp1(tp1);
        log.debug("🔄 [MAPPING] TP1 extracted: {}", tp1);

        Double tp2 = getDouble(data, "tp2");
        signal.setTp2(tp2);
        log.debug("🔄 [MAPPING] TP2 extracted: {}", tp2);

        Double tp3 = getDouble(data, "tp3");
        signal.setTp3(tp3);
        log.debug("🔄 [MAPPING] TP3 extracted: {}", tp3);

        Double tp4 = getDouble(data, "tp4");
        signal.setTp4(tp4);
        log.debug("🔄 [MAPPING] TP4 extracted: {}", tp4);

        Double stopLoss = getDouble(data, "stop_loss");
        signal.setStopLoss(stopLoss);
        log.debug("🔄 [MAPPING] Stop loss extracted: {}", stopLoss);

        String fullMessage = getString(data, "full_message");
        signal.setFullMessage(fullMessage);
        log.debug("🔄 [MAPPING] Full message extracted (length: {})", fullMessage != null ? fullMessage.length() : 0);

        signal.setChannel("TELEGRAM");
        signal.setTimestamp(OffsetDateTime.now());

        Double quantity = getDouble(data, "quantity");
        signal.setQuantity(quantity);
        log.debug("🔄 [MAPPING] Quantity extracted: {}", quantity);

        log.debug("🔄 [MAPPING] Signal entity mapping completed successfully");
        return signal;
    }

    /**
     * Map signal data to ExecuteTradeRequest
     */
    private ExecuteTradeRequest mapSignalToTradeRequest(Map<String, Object> data, Long signalId) {
        ExecuteTradeRequest request = new ExecuteTradeRequest();

        // Convert LONG/SHORT to BUY/SELL
        String setupType = getString(data, "setup_type");
        request.setSide(setupType.equalsIgnoreCase("LONG") ? "BUY" : "SELL");

        request.setPair(getString(data, "pair"));
        request.setEntry(getDouble(data, "entry"));
        // Convert Double leverage to Integer safely
        Double leverageDouble = getDouble(data, "leverage");
        request.setLeverage(leverageDouble != null ? leverageDouble.intValue() : 1);
        request.setTp1(getDouble(data, "tp1"));
        request.setTp2(getDouble(data, "tp2"));
        request.setTp3(getDouble(data, "tp3"));
        request.setTp4(getDouble(data, "tp4"));
        request.setStopLoss(getDouble(data, "stop_loss"));
        request.setQuantity(getDouble(data, "quantity")); // Can be null, auto-calculated
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
