package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinancePositionDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller for Binance Futures API endpoints
 * Provides access to real-time position data and order management
 */
@Slf4j
@RestController
@RequestMapping("/api/binance")
@CrossOrigin(origins = "*")
public class BinanceController {

    @Autowired
    private TradeService tradeService;

    /**
     * Get all open positions from Binance Futures
     *
     * Returns real-time position data including:
     * - Symbol, side (LONG/SHORT), entry price, mark price
     * - Quantity, leverage, P&L, liquidation price
     * - Related open orders (entry, TP, SL)
     *
     * @return List of open positions from Binance
     */
    @GetMapping("/openPositions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<BinancePositionDTO>> getOpenPositions() {
        try {
            log.info("📊 Fetching open positions from Binance");
            List<BinancePositionDTO> positions = tradeService.getOpenPositionsFromBinance();

            log.info("✅ Retrieved {} open positions", positions.size());
            return ResponseEntity.ok(positions);

        } catch (Exception e) {
            log.error("❌ Error fetching open positions: {}", e.getMessage());
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Close a position on Binance with a MARKET order
     *
     * Uses reduceOnly=true to ensure only closing existing position
     *
     * @param request Contains: symbol, side, quantity
     * @return Success/failure response
     */
    @PostMapping("/closePosition")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Map<String, Object>> closePosition(@RequestBody Map<String, Object> request) {
        try {
            String symbol = (String) request.get("symbol");
            String side = (String) request.get("side");
            Double quantity = ((Number) request.get("quantity")).doubleValue();

            log.info("📉 Closing position: {} {} Qty={}", side, symbol, quantity);

            boolean success = tradeService.closePositionOnBinanceMarket(symbol, side, quantity);

            if (success) {
                log.info("✅ Position closed successfully");
                return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Position closed successfully on Binance",
                    "symbol", symbol,
                    "side", side,
                    "quantity", quantity
                ));
            } else {
                log.warn("⚠️ Failed to close position");
                return ResponseEntity.status(400).body(Map.of(
                    "status", "FAILED",
                    "message", "Failed to close position on Binance",
                    "symbol", symbol
                ));
            }

        } catch (Exception e) {
            log.error("❌ Error closing position: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                "status", "ERROR",
                "message", "Error: " + e.getMessage()
            ));
        }
    }
}
