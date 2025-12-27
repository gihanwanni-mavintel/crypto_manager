package mav_intel.com.Intelligent_Crypto_User_Management.service;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to automatically execute trades based on trading signals
 * Can be triggered by:
 * 1. Telegram signal webhooks (from Python backend)
 * 2. Manual API calls
 * 3. Scheduled tasks
 */
@Slf4j
@Service
public class AutoTradeExecutor {

    @Autowired
    private TradeService tradeService;

    @Autowired
    private SignalRepository signalRepository;

    @Value("${trade.max-concurrent-trades:5}")
    private int maxConcurrentTrades;

    @Value("${trade.min-signal-confidence:0.8}")
    private double minSignalConfidence;

    /**
     * Execute trade from signal asynchronously
     */
    @Async
    public void executeTradeAsync(Signal signal) {
        try {
            log.info("🔄 Async trade execution started for signal ID: {}", signal.getId());
            ExecuteTradeRequest request = convertSignalToTradeRequest(signal);
            ExecuteTradeResponse response = tradeService.executeTrade(request);
            log.info("✅ Async trade execution completed: {}", response.getStatus());
        } catch (Exception e) {
            log.error("❌ Error in async trade execution: {}", e.getMessage(), e);
        }
    }

    /**
     * Execute trade synchronously (for immediate execution)
     */
    public ExecuteTradeResponse executeTradeSync(Signal signal) {
        try {
            log.info("🚀 Synchronous trade execution for signal ID: {}", signal.getId());
            ExecuteTradeRequest request = convertSignalToTradeRequest(signal);
            return tradeService.executeTrade(request);
        } catch (Exception e) {
            log.error("❌ Error in sync trade execution: {}", e.getMessage(), e);
            return new ExecuteTradeResponse(signal.getId(), signal.getPair(), "FAILED", "Execution error: " + e.getMessage());
        }
    }

    /**
     * Execute all pending signals
     */
    public void executePendingSignals() {
        try {
            log.info("🔍 Checking for pending signals to execute...");
            List<Signal> signals = signalRepository.findAll();

            int executeCount = 0;
            for (Signal signal : signals) {
                if (executeCount >= maxConcurrentTrades) {
                    log.warn("⚠️ Max concurrent trades ({}) reached. Stopping execution.", maxConcurrentTrades);
                    break;
                }

                if (shouldExecuteSignal(signal)) {
                    executeTradeAsync(signal);
                    executeCount++;
                }
            }

            log.info("✅ Pending signal execution complete. Executed: {} trades", executeCount);
        } catch (Exception e) {
            log.error("❌ Error executing pending signals: {}", e.getMessage(), e);
        }
    }

    /**
     * Determine if a signal should be executed
     * Can be extended with more validation logic
     */
    private boolean shouldExecuteSignal(Signal signal) {
        // Check required fields
        if (signal.getPair() == null || signal.getEntry() == null) {
            log.warn("⚠️ Signal {} missing required fields", signal.getId());
            return false;
        }

        // Check signal confidence (can be enhanced with more logic)
        if (signal.getSetupType() == null) {
            log.warn("⚠️ Signal {} missing setup type", signal.getId());
            return false;
        }

        log.info("✓ Signal {} is valid for execution", signal.getId());
        return true;
    }

    /**
     * Convert Signal to ExecuteTradeRequest
     */
    private ExecuteTradeRequest convertSignalToTradeRequest(Signal signal) {
        ExecuteTradeRequest request = new ExecuteTradeRequest();

        // Use LONG/SHORT directly (matching Signal.setupType format)
        request.setSide(signal.getSetupType()); // LONG or SHORT

        request.setPair(signal.getPair());
        request.setEntry(signal.getEntry());
        request.setLeverage(20); // Default 20x leverage
        request.setTakeProfit(signal.getTakeProfit()); // Single TP calculated by Python
        request.setStopLoss(signal.getStopLoss());
        request.setQuantity(null); // Auto-calculated by TradeService
        request.setSignalId(signal.getId());

        return request;
    }

    /**
     * Get execution statistics
     */
    public ExecutionStats getExecutionStats() {
        ExecutionStats stats = new ExecutionStats();
        stats.setMaxConcurrentTrades(maxConcurrentTrades);
        stats.setMinSignalConfidence(minSignalConfidence);
        stats.setTimestamp(System.currentTimeMillis());
        return stats;
    }

    /**
     * Execution statistics DTO
     */
    public static class ExecutionStats {
        public int maxConcurrentTrades;
        public double minSignalConfidence;
        public long timestamp;

        // Getters and setters
        public int getMaxConcurrentTrades() { return maxConcurrentTrades; }
        public void setMaxConcurrentTrades(int max) { this.maxConcurrentTrades = max; }

        public double getMinSignalConfidence() { return minSignalConfidence; }
        public void setMinSignalConfidence(double min) { this.minSignalConfidence = min; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long ts) { this.timestamp = ts; }
    }
}
