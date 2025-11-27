package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinancePositionDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.service.PositionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ✅ POSITION MANAGEMENT REST CONTROLLER
 * Handles API requests for position management
 * All endpoints follow REST conventions and Binance Futures logic
 */
@Slf4j
@RestController
@RequestMapping("/api/positions")
@CrossOrigin(origins = "http://localhost:3000, http://localhost:8081")
public class PositionController {

    @Autowired
    private PositionService positionService;

    /**
     * ✅ GET /api/positions/active
     * Fetch all active (open) positions from Binance
     *
     * @return List of open positions with full details
     */
    @GetMapping("/active")
    public ResponseEntity<?> getActivePositions() {
        try {
            log.info("📊 [API] Fetching active positions...");

            List<BinancePositionDTO> positions = positionService.getActivePositions();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Successfully fetched " + positions.size() + " active positions");
            response.put("data", positions);
            response.put("count", positions.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error fetching positions: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Failed to fetch positions: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * ✅ PUT /api/positions/{symbol}/stop-loss
     * Update stop loss for a specific position
     *
     * @param symbol Binance symbol (e.g., BTCUSDT)
     * @param request Request body with stopLoss, quantity, and pricePrecision
     * @return Success confirmation
     */
    @PutMapping("/{symbol}/stop-loss")
    public ResponseEntity<?> updateStopLoss(
            @PathVariable String symbol,
            @RequestBody Map<String, Object> request) {
        try {
            log.info("🔄 [API] Updating SL for {}", symbol);

            double newStopLoss = ((Number) request.get("stopLoss")).doubleValue();
            double quantity = ((Number) request.get("quantity")).doubleValue();
            String side = (String) request.get("side");
            int pricePrecision = ((Number) request.getOrDefault("pricePrecision", 2)).intValue();

            boolean success = positionService.updateStopLoss(symbol, side, quantity, newStopLoss, pricePrecision);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("symbol", symbol);
            response.put("stopLoss", newStopLoss);
            response.put("message", success ? "Stop Loss updated successfully" : "Failed to update Stop Loss");

            return success ? ResponseEntity.ok(response) : ResponseEntity.status(400).body(response);

        } catch (Exception e) {
            log.error("❌ Error updating SL: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating stop loss: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * ✅ PUT /api/positions/{symbol}/take-profits
     * Update all take profit levels for a position
     *
     * @param symbol Binance symbol (e.g., BTCUSDT)
     * @param request Request body with takeProfitLevels array
     * @return Success confirmation
     */
    @PutMapping("/{symbol}/take-profits")
    public ResponseEntity<?> updateTakeProfits(
            @PathVariable String symbol,
            @RequestBody Map<String, Object> request) {
        try {
            log.info("🔄 [API] Updating TP levels for {}", symbol);

            @SuppressWarnings("unchecked")
            List<Map<String, Double>> takeProfitLevels = (List<Map<String, Double>>) request.get("takeProfitLevels");
            String side = (String) request.get("side");
            int pricePrecision = ((Number) request.getOrDefault("pricePrecision", 2)).intValue();

            boolean success = positionService.updateTakeProfits(symbol, side, takeProfitLevels, pricePrecision);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("symbol", symbol);
            response.put("takeProfitLevels", takeProfitLevels.size());
            response.put("message", success ? "Take Profits updated successfully" : "Failed to update Take Profits");

            return success ? ResponseEntity.ok(response) : ResponseEntity.status(400).body(response);

        } catch (Exception e) {
            log.error("❌ Error updating TP: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error updating take profits: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * ✅ POST /api/positions/{symbol}/close
     * Close a position completely by placing market exit order
     *
     * @param symbol Binance symbol (e.g., BTCUSDT)
     * @param request Request body with quantity and side
     * @return Success confirmation
     */
    @PostMapping("/{symbol}/close")
    public ResponseEntity<?> closePosition(
            @PathVariable String symbol,
            @RequestBody Map<String, Object> request) {
        try {
            log.info("🔴 [API] Closing position: {}", symbol);

            double quantity = ((Number) request.get("quantity")).doubleValue();
            String side = (String) request.get("side");

            boolean success = positionService.closePosition(symbol, side, quantity);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("symbol", symbol);
            response.put("quantity", quantity);
            response.put("message", success ? "Position closed successfully" : "Failed to close position");

            return success ? ResponseEntity.ok(response) : ResponseEntity.status(400).body(response);

        } catch (Exception e) {
            log.error("❌ Error closing position: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Error closing position: " + e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    /**
     * ✅ Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "PositionManager");
        return ResponseEntity.ok(response);
    }
}
