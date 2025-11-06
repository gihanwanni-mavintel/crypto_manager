package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/trades")
@PreAuthorize("hasRole('USER')")
public class TradeController {

    @Autowired
    private TradeService tradeService;

    /**
     * Execute a new trade
     * POST /api/trades/execute
     */
    @PostMapping("/execute")
    public ResponseEntity<ExecuteTradeResponse> executeTrade(@RequestBody ExecuteTradeRequest request) {
        log.info("📥 Trade execution request: {} {}", request.getSide(), request.getPair());
        ExecuteTradeResponse response = tradeService.executeTrade(request);

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Close an open position
     * POST /api/trades/close/{tradeId}
     */
    @PostMapping("/close/{tradeId}")
    public ResponseEntity<ExecuteTradeResponse> closePosition(@PathVariable Long tradeId) {
        log.info("📥 Close position request for trade ID: {}", tradeId);
        ExecuteTradeResponse response = tradeService.closePosition(tradeId);

        if ("SUCCESS".equals(response.getStatus())) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    /**
     * Get all trades
     * GET /api/trades
     */
    @GetMapping
    public ResponseEntity<List<Trade>> getAllTrades() {
        log.info("📥 Get all trades request");
        List<Trade> trades = tradeService.getAllTrades();
        return ResponseEntity.ok(trades);
    }

    /**
     * Get trade by ID
     * GET /api/trades/{tradeId}
     */
    @GetMapping("/{tradeId}")
    public ResponseEntity<Trade> getTrade(@PathVariable Long tradeId) {
        log.info("📥 Get trade request for ID: {}", tradeId);
        Trade trade = tradeService.getTrade(tradeId);
        return ResponseEntity.ok(trade);
    }

    /**
     * Get open trades
     * GET /api/trades/status/open
     */
    @GetMapping("/status/open")
    public ResponseEntity<List<Trade>> getOpenTrades() {
        log.info("📥 Get open trades request");
        List<Trade> trades = tradeService.getOpenTrades();
        return ResponseEntity.ok(trades);
    }

    /**
     * Get trades by pair
     * GET /api/trades/pair/{pair}
     */
    @GetMapping("/pair/{pair}")
    public ResponseEntity<List<Trade>> getTradesByPair(@PathVariable String pair) {
        log.info("📥 Get trades for pair: {}", pair);
        List<Trade> trades = tradeService.getTradesByPair(pair);
        return ResponseEntity.ok(trades);
    }
}
