package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    /**
     * Execute a new trade based on the request
     */
    public ExecuteTradeResponse executeTrade(ExecuteTradeRequest request) {
        try {
            // Validate request
            if (request.getPair() == null || request.getSide() == null || request.getEntry() == null) {
                return new ExecuteTradeResponse(null, request.getPair(), "FAILED", "Missing required fields");
            }

            log.info("🚀 Executing trade: {} {} @ ${}", request.getSide(), request.getPair(), request.getEntry());

            // 1. Create Trade record in database
            Trade trade = new Trade();
            trade.setPair(request.getPair());
            trade.setSide(request.getSide());
            trade.setEntryPrice(request.getEntry());
            trade.setEntryQuantity(request.getQuantity() != null ? request.getQuantity() : 0.0);
            trade.setLeverage(request.getLeverage() != null ? request.getLeverage() : 1);
            trade.setStopLoss(request.getStopLoss());
            trade.setTp1(request.getTp1());
            trade.setTp2(request.getTp2());
            trade.setTp3(request.getTp3());
            trade.setTp4(request.getTp4());
            trade.setStatus("PENDING");
            trade.setOpenedAt(OffsetDateTime.now());
            trade.setSignalId(request.getSignalId());

            // Save initial trade record
            Trade savedTrade = tradeRepository.save(trade);
            log.info("✅ Trade record created with ID: {}", savedTrade.getId());

            // 2. Place order on Binance
            if (futuresClient != null) {
                boolean orderPlaced = placeBinanceOrder(savedTrade);
                if (orderPlaced) {
                    trade.setStatus("OPEN");
                    tradeRepository.save(trade);
                    return new ExecuteTradeResponse(
                        savedTrade.getId(),
                        request.getPair(),
                        "SUCCESS",
                        "Order placed successfully on Binance"
                    );
                } else {
                    trade.setStatus("FAILED");
                    tradeRepository.save(trade);
                    return new ExecuteTradeResponse(
                        savedTrade.getId(),
                        request.getPair(),
                        "FAILED",
                        "Failed to place order on Binance"
                    );
                }
            } else {
                log.warn("⚠️ Binance client not configured. Trade saved in database but order not placed");
                trade.setStatus("OPEN");
                tradeRepository.save(trade);
                return new ExecuteTradeResponse(
                    savedTrade.getId(),
                    request.getPair(),
                    "SUCCESS",
                    "Trade recorded (Binance client not configured)"
                );
            }

        } catch (Exception e) {
            log.error("❌ Error executing trade: {}", e.getMessage(), e);
            return new ExecuteTradeResponse(null, request.getPair(), "FAILED", "Error: " + e.getMessage());
        }
    }

    /**
     * Place order on Binance Futures (LIMIT or MARKET)
     */
    private boolean placeBinanceOrder(Trade trade) {
        try {
            double balance = getBalance();
            if (balance <= 10) {
                log.error("❌ Insufficient balance. USDT={}", balance);
                return false;
            }

            String symbol = trade.getPair();
            String side = trade.getSide().equalsIgnoreCase("LONG") ? "BUY" : "SELL";

            // 1. Set leverage
            setLeverage(symbol, trade.getLeverage());

            // 2. Place LIMIT order at entry price
            LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
            orderParams.put("symbol", symbol);
            orderParams.put("side", side);
            orderParams.put("type", "LIMIT");
            orderParams.put("timeInForce", "GTC"); // Good Till Cancel
            orderParams.put("quantity", calculateQuantity(trade.getEntryQuantity(), trade.getLeverage(), balance));
            orderParams.put("price", trade.getEntryPrice()); // LIMIT order at exact entry price
            orderParams.put("recvWindow", 60000);

            log.info("📍 Placing LIMIT order: {} {} @ ${} Qty={}",
                side, symbol, trade.getEntryPrice(), orderParams.get("quantity"));

            String orderResponse = futuresClient.account().newOrder(orderParams);
            JSONObject resp = new JSONObject(orderResponse);
            String orderId = resp.optString("orderId", "");
            String status = resp.optString("status", "FAILED");
            double executedQty = resp.optDouble("executedQty", 0.0);

            log.info("✅ LIMIT Order placed: {} {} Qty={} Status={}", side, symbol, executedQty, status);

            trade.setBinanceOrderId(orderId);

            // 3. Place Stop-Loss
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                placeStopLoss(symbol, side, executedQty, trade.getStopLoss());
            }

            // 4. Place Take-Profits
            if (trade.getTp1() != null && trade.getTp1() > 0) {
                placeTakeProfit(symbol, side, executedQty, trade.getTp1(), "TP1");
            }
            if (trade.getTp2() != null && trade.getTp2() > 0) {
                placeTakeProfit(symbol, side, executedQty, trade.getTp2(), "TP2");
            }
            if (trade.getTp3() != null && trade.getTp3() > 0) {
                placeTakeProfit(symbol, side, executedQty, trade.getTp3(), "TP3");
            }
            if (trade.getTp4() != null && trade.getTp4() > 0) {
                placeTakeProfit(symbol, side, executedQty, trade.getTp4(), "TP4");
            }

            log.info("✅ All orders placed for {}", symbol);
            return true;

        } catch (Exception e) {
            log.error("❌ Error placing Binance order: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Get USDT balance from Binance
     */
    private double getBalance() {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000);
            String result = futuresClient.account().futuresAccountBalance(params);
            var balances = new org.json.JSONArray(result);
            for (int i = 0; i < balances.length(); i++) {
                var balance = balances.getJSONObject(i);
                if ("USDT".equals(balance.getString("asset"))) {
                    double available = balance.getDouble("availableBalance");
                    log.info("💰 USDT Balance: {}", available);
                    return available;
                }
            }
        } catch (Exception e) {
            log.error("❌ Error fetching balance: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Calculate quantity based on available balance and leverage
     * If quantity is provided, use it; otherwise calculate from balance
     */
    private double calculateQuantity(Double requestedQty, int leverage, double balance) {
        if (requestedQty != null && requestedQty > 0) {
            return requestedQty; // Use provided quantity
        }
        // Default: Use 50% of available balance divided by leverage
        double quantity = (balance * 0.5) / leverage;
        log.info("📊 Auto-calculated quantity: {} (50% of balance / leverage)", quantity);
        return Math.max(quantity, 0.001); // Minimum quantity
    }

    /**
     * Set leverage for symbol
     */
    private void setLeverage(String symbol, int leverage) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("leverage", leverage);
            params.put("recvWindow", 60000);
            futuresClient.account().changeInitialLeverage(params);
            log.info("⚙️ Leverage set to {}x for {}", leverage, symbol);
        } catch (Exception e) {
            log.warn("⚠️ Error setting leverage: {}", e.getMessage());
        }
    }

    /**
     * Place Stop-Loss order
     */
    private boolean placeStopLoss(String symbol, String side, double qty, double stopPrice) {
        try {
            String slSide = side.equals("BUY") ? "SELL" : "BUY";
            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", slSide);
            slParams.put("type", "STOP_MARKET");
            slParams.put("quantity", qty);
            slParams.put("stopPrice", stopPrice);
            slParams.put("timeInForce", "GTC");
            slParams.put("recvWindow", 60000);

            String resp = futuresClient.account().newOrder(slParams);
            log.info("🛑 Stop-Loss placed: {}", resp);
            return true;
        } catch (Exception e) {
            log.error("❌ Error placing Stop-Loss: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Place Take-Profit order
     */
    private void placeTakeProfit(String symbol, String side, double qty, double tpPrice, String label) {
        try {
            String tpSide = side.equals("BUY") ? "SELL" : "BUY";
            LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
            tpParams.put("symbol", symbol);
            tpParams.put("side", tpSide);
            tpParams.put("type", "TAKE_PROFIT_MARKET");
            tpParams.put("quantity", qty);
            tpParams.put("stopPrice", tpPrice);
            tpParams.put("timeInForce", "GTC");
            tpParams.put("recvWindow", 60000);

            String resp = futuresClient.account().newOrder(tpParams);
            log.info("📈 {} placed: {}", label, resp);
        } catch (Exception e) {
            log.error("❌ Error placing {}: {}", label, e.getMessage());
        }
    }

    /**
     * Close an open position
     */
    public ExecuteTradeResponse closePosition(Long tradeId) {
        try {
            Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new RuntimeException("Trade not found"));

            if (!trade.getStatus().equals("OPEN")) {
                return new ExecuteTradeResponse(tradeId, trade.getPair(), "FAILED", "Trade is not open");
            }

            // Close position on Binance if client available
            if (futuresClient != null) {
                closePositionOnBinance(trade);
            }

            // Update database
            trade.setStatus("CLOSED");
            trade.setClosedAt(OffsetDateTime.now());
            tradeRepository.save(trade);

            return new ExecuteTradeResponse(tradeId, trade.getPair(), "SUCCESS", "Position closed");
        } catch (Exception e) {
            log.error("❌ Error closing position: {}", e.getMessage());
            return new ExecuteTradeResponse(tradeId, "", "FAILED", "Error: " + e.getMessage());
        }
    }

    /**
     * Close position on Binance
     */
    private void closePositionOnBinance(Trade trade) {
        try {
            String symbol = trade.getPair();
            String side = trade.getSide().equalsIgnoreCase("LONG") ? "SELL" : "BUY";

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("side", side);
            params.put("type", "MARKET");
            params.put("quantity", trade.getEntryQuantity());
            params.put("reduceOnly", "true");
            params.put("recvWindow", 60000);

            String resp = futuresClient.account().newOrder(params);
            JSONObject response = new JSONObject(resp);
            String status = response.optString("status", "");

            log.info("✅ Position closed on Binance: {}", status);
        } catch (Exception e) {
            log.warn("⚠️ Error closing position on Binance: {}", e.getMessage());
        }
    }

    /**
     * Get all trades
     */
    public List<Trade> getAllTrades() {
        return tradeRepository.findAllByOrderByOpenedAtDesc();
    }

    /**
     * Get trade by ID
     */
    public Trade getTrade(Long tradeId) {
        return tradeRepository.findById(tradeId)
            .orElseThrow(() -> new RuntimeException("Trade not found"));
    }

    /**
     * Get open trades
     */
    public List<Trade> getOpenTrades() {
        return tradeRepository.findByStatus("OPEN");
    }

    /**
     * Get trades by pair
     */
    public List<Trade> getTradesByPair(String pair) {
        return tradeRepository.findByPair(pair);
    }
}
