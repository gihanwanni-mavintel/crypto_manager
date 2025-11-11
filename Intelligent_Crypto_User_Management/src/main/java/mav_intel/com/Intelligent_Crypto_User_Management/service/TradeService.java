package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    // Margin requirements (hardcoded for testnet)
    private static final double INITIAL_MARGIN_REQUIREMENT = 1.2;      // $1.2 USDT
    private static final double MAINTENANCE_MARGIN_REQUIREMENT = 1.2;  // $1.2 USDT

    /**
     * Execute a new trade based on the request
     */
    public ExecuteTradeResponse executeTrade(ExecuteTradeRequest request) {
        try {
            log.info("═══════════════════════════════════════════════════════════════════");
            log.info("🚀 [TRADE] Starting trade execution");

            // Validate request
            log.debug("🚀 [TRADE] Validating trade request...");
            if (request.getPair() == null || request.getSide() == null || request.getEntry() == null) {
                log.error("❌ [TRADE] Validation failed - Missing required fields");
                log.error("  - Pair: {}", request.getPair());
                log.error("  - Side: {}", request.getSide());
                log.error("  - Entry: {}", request.getEntry());
                return new ExecuteTradeResponse(null, request.getPair(), "FAILED", "Missing required fields");
            }
            log.debug("✅ [TRADE] Validation passed");

            log.info("🚀 [TRADE] Trade details:");
            log.info("  - Pair: {}", request.getPair());
            log.info("  - Side: {}", request.getSide());
            log.info("  - Entry Price: ${}", request.getEntry());
            log.info("  - Leverage: {}x", request.getLeverage());
            log.info("  - Quantity: {}", request.getQuantity());
            log.info("  - Stop Loss: {}", request.getStopLoss());
            log.info("  - TP1: {}", request.getTp1());
            log.info("  - TP2: {}", request.getTp2());
            log.info("  - TP3: {}", request.getTp3());
            log.info("  - TP4: {}", request.getTp4());
            log.info("  - Signal ID: {}", request.getSignalId());

            // 1. Create Trade record in database
            log.info("💾 [DATABASE] Creating trade record...");
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
            log.info("✅ [DATABASE] Trade record saved with ID: {}", savedTrade.getId());
            log.debug("✅ [DATABASE] Trade entity: Pair={}, Side={}, EntryPrice={}, Leverage={}, Status={}",
                savedTrade.getPair(), savedTrade.getSide(), savedTrade.getEntryPrice(),
                savedTrade.getLeverage(), savedTrade.getStatus());

            // 2. Place order on Binance
            log.info("⚙️  [BINANCE] Binance client configured: {}", futuresClient != null);

            if (futuresClient != null) {
                log.info("🔗 [BINANCE] Attempting to place order on Binance...");
                boolean orderPlaced = placeBinanceOrder(savedTrade);

                if (orderPlaced) {
                    log.info("✅ [BINANCE] Order placed successfully!");
                    trade.setStatus("OPEN");
                    tradeRepository.save(trade);
                    log.info("✅ [DATABASE] Trade status updated to OPEN");

                    log.info("═══════════════════════════════════════════════════════════════════");
                    return new ExecuteTradeResponse(
                        savedTrade.getId(),
                        request.getPair(),
                        "SUCCESS",
                        "Order placed successfully on Binance"
                    );
                } else {
                    log.error("❌ [BINANCE] Failed to place order on Binance");
                    trade.setStatus("FAILED");
                    tradeRepository.save(trade);
                    log.info("✅ [DATABASE] Trade status updated to FAILED");

                    log.info("═══════════════════════════════════════════════════════════════════");
                    return new ExecuteTradeResponse(
                        savedTrade.getId(),
                        request.getPair(),
                        "FAILED",
                        "Failed to place order on Binance"
                    );
                }
            } else {
                log.warn("⚠️  [BINANCE] Binance client NOT configured. Trade saved but order NOT placed");
                trade.setStatus("OPEN");
                tradeRepository.save(trade);
                log.info("✅ [DATABASE] Trade status updated to OPEN (no Binance execution)");

                log.info("═══════════════════════════════════════════════════════════════════");
                return new ExecuteTradeResponse(
                    savedTrade.getId(),
                    request.getPair(),
                    "SUCCESS",
                    "Trade recorded (Binance client not configured)"
                );
            }

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════════════");
            log.error("❌ [TRADE] Exception while executing trade");
            log.error("❌ [TRADE] Exception type: {}", e.getClass().getName());
            log.error("❌ [TRADE] Exception message: {}", e.getMessage());
            log.error("❌ [TRADE] Full exception: ", e);
            log.error("═══════════════════════════════════════════════════════════════════");

            return new ExecuteTradeResponse(null, request.getPair(), "FAILED", "Error: " + e.getMessage());
        }
    }

    /**
     * Place order on Binance Futures using Batch Orders endpoint (Entry + SL + Multiple TPs)
     * Sends all orders in a single atomic API call to /fapi/v1/batchOrders
     */
    private boolean placeBinanceOrder(Trade trade) {
        try {
            log.info("🔗 [BINANCE] Starting Binance batch order placement...");

            // Get balance
            log.debug("💰 [BINANCE] Fetching USDT balance...");
            double balance = getBalance();
            log.info("💰 [BINANCE] Current USDT balance: ${}", balance);

            if (balance <= 5) {
                log.error("❌ [BINANCE] Insufficient balance. USDT={} (minimum: $10)", balance);
                return false;
            }
            log.debug("✅ [BINANCE] Balance check passed");

            // Display margin requirements
            log.info("═══════════════════════════════════════════════════════════════════");
            log.info("💰 [MARGIN] Margin Requirements (Testnet Hardcoded):");
            log.info("  - Initial Margin: ${}", INITIAL_MARGIN_REQUIREMENT);
            log.info("  - Maintenance Margin: ${}", MAINTENANCE_MARGIN_REQUIREMENT);
            log.info("  - Total Balance: ${}", balance);
            log.info("  - Available Balance (after margins): ${}", balance - INITIAL_MARGIN_REQUIREMENT - MAINTENANCE_MARGIN_REQUIREMENT);
            log.info("═══════════════════════════════════════════════════════════════════");

            String symbol = trade.getPair();
            // Handle both LONG/SHORT and BUY/SELL formats
            String tradeSide = trade.getSide();
            String side = (tradeSide.equalsIgnoreCase("LONG") || tradeSide.equalsIgnoreCase("BUY")) ? "BUY" : "SELL";

            log.debug("🔗 [BINANCE] Order details: Symbol={}, Trade Side={}, Binance Side={}", symbol, tradeSide, side);

            // 1. Set leverage
            log.info("⚙️  [BINANCE] Setting leverage to {}x for {}...", trade.getLeverage(), symbol);
            setLeverage(symbol, trade.getLeverage());

            // 2. Fetch symbol filters for precision formatting
            log.info("🔍 [BINANCE] Fetching symbol filters for precision formatting...");
            Map<String, Object> symbolFilters = getSymbolFilters(symbol);

            if (symbolFilters == null) {
                log.error("❌ [BINANCE] Failed to fetch symbol filters. Cannot place order safely.");
                return false;
            }

            // 3. Calculate quantities
            double calculatedQty = calculateQuantity(trade.getEntryQuantity(), trade.getLeverage(), balance, trade.getEntryPrice());
            double formattedQty = formatQuantity(calculatedQty, symbolFilters);
            double formattedPrice = formatPrice(trade.getEntryPrice(), symbolFilters);

            // 4. Build batch orders array
            log.info("📦 [BINANCE] Building batch orders for Entry + SL + TPs...");
            JSONArray batchOrders = new JSONArray();

            // Order 1: Entry Order (LIMIT)
            JSONObject entryOrder = new JSONObject();
            entryOrder.put("symbol", symbol);
            entryOrder.put("side", side);
            entryOrder.put("type", "LIMIT");
            entryOrder.put("timeInForce", "GTC");
            entryOrder.put("quantity", String.valueOf(formattedQty)); // CRITICAL: String format for batch
            entryOrder.put("price", String.valueOf(formattedPrice));  // CRITICAL: String format for batch
            batchOrders.put(entryOrder);

            log.info("✅ [BATCH] Order 1 (Entry): {} {} {} @ ${} qty={}",
                symbol, side, "LIMIT", formattedPrice, formattedQty);

            String closeSide = side.equals("BUY") ? "SELL" : "BUY";

            // Order 2: Stop Loss (if configured)
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                JSONObject slOrder = new JSONObject();
                slOrder.put("symbol", symbol);
                slOrder.put("side", closeSide);
                slOrder.put("type", "STOP_MARKET");
                slOrder.put("quantity", String.valueOf(formattedQty));
                slOrder.put("stopPrice", String.valueOf(trade.getStopLoss()));
                slOrder.put("reduceOnly", "true"); // CRITICAL: Prevent opening new position
                batchOrders.put(slOrder);
                log.info("✅ [BATCH] Order 2 (SL): {} {} {} @ ${} (stopPrice) qty={} reduceOnly=true",
                    symbol, closeSide, "STOP_MARKET", trade.getStopLoss(), formattedQty);
            }

            // Orders 3-6: Take Profit orders (distribute quantity across TPs)
            int tpIndex = 3;
            List<Double> tpPrices = new ArrayList<>();
            List<Double> tpQuantities = new ArrayList<>();

            if (trade.getTp1() != null && trade.getTp1() > 0) {
                tpPrices.add(trade.getTp1());
            }
            if (trade.getTp2() != null && trade.getTp2() > 0) {
                tpPrices.add(trade.getTp2());
            }
            if (trade.getTp3() != null && trade.getTp3() > 0) {
                tpPrices.add(trade.getTp3());
            }
            if (trade.getTp4() != null && trade.getTp4() > 0) {
                tpPrices.add(trade.getTp4());
            }

            if (!tpPrices.isEmpty()) {
                // Distribute quantity evenly across TP levels (with proper formatting and remainder handling)
                tpQuantities = distributeQuantity(formattedQty, tpPrices.size(), symbolFilters);

                for (int i = 0; i < tpPrices.size(); i++) {
                    JSONObject tpOrder = new JSONObject();
                    tpOrder.put("symbol", symbol);
                    tpOrder.put("side", closeSide);
                    tpOrder.put("type", "TAKE_PROFIT_MARKET");
                    tpOrder.put("quantity", String.valueOf(tpQuantities.get(i)));
                    tpOrder.put("stopPrice", String.valueOf(tpPrices.get(i)));
                    tpOrder.put("reduceOnly", "true"); // CRITICAL: Prevent opening new position
                    batchOrders.put(tpOrder);
                    log.info("✅ [BATCH] Order {} (TP{}): {} {} {} @ ${} (stopPrice) qty={} reduceOnly=true",
                        tpIndex, i + 1, symbol, closeSide, "TAKE_PROFIT_MARKET", tpPrices.get(i), tpQuantities.get(i));
                    tpIndex++;
                }
            } else {
                log.warn("⚠️  [BINANCE] No Take-Profit levels configured");
            }

            // 5. Place orders sequentially with reduceOnly protection
            // Note: binance-futures-connector-java doesn't support /fapi/v1/batchOrders endpoint,
            // so we place orders individually but with reduceOnly=true for TP/SL orders
            log.info("═══════════════════════════════════════════════════════════════════");
            log.info("📦 [BINANCE] Placing orders sequentially (Entry, SL, TPs)...");
            log.info("═══════════════════════════════════════════════════════════════════");

            boolean allSuccess = true;
            int orderCount = 0;

            // Place Entry Order
            try {
                log.info("📍 [BINANCE] Placing Entry Order...");
                LinkedHashMap<String, Object> entryParams = new LinkedHashMap<>();
                entryParams.put("symbol", symbol);
                entryParams.put("side", side);
                entryParams.put("type", "LIMIT");
                entryParams.put("timeInForce", "GTC");
                entryParams.put("quantity", formattedQty);
                entryParams.put("price", formattedPrice);
                entryParams.put("recvWindow", 60000);

                String entryResponse = futuresClient.account().newOrder(entryParams);
                JSONObject entryResp = new JSONObject(entryResponse);
                String entryOrderId = entryResp.optString("orderId", "");
                String entryStatus = entryResp.optString("status", "FAILED");

                if ("NEW".equals(entryStatus) || "PARTIALLY_FILLED".equals(entryStatus)) {
                    log.info("✅ [BINANCE] Entry Order placed successfully - ID: {}", entryOrderId);
                    trade.setBinanceOrderId(entryOrderId);
                    orderCount++;
                } else {
                    log.error("❌ [BINANCE] Entry Order failed - Status: {}", entryStatus);
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.error("❌ [BINANCE] Exception placing Entry Order: {}", e.getMessage());
                allSuccess = false;
            }

            // Place Stop Loss Order (if configured)
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                try {
                    log.info("🛑 [BINANCE] Placing Stop Loss Order...");
                    double formattedSlPrice = formatPrice(trade.getStopLoss(), symbolFilters);
                    LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
                    slParams.put("symbol", symbol);
                    slParams.put("side", closeSide);
                    slParams.put("type", "STOP_MARKET");
                    slParams.put("quantity", formattedQty);
                    slParams.put("stopPrice", formattedSlPrice); // CRITICAL: Format stopPrice
                    slParams.put("reduceOnly", "true"); // CRITICAL: Prevent opening new position
                    slParams.put("recvWindow", 60000);

                    String slResponse = futuresClient.account().newOrder(slParams);
                    JSONObject slResp = new JSONObject(slResponse);
                    String slOrderId = slResp.optString("orderId", "");
                    String slStatus = slResp.optString("status", "FAILED");

                    if ("NEW".equals(slStatus) || "PARTIALLY_FILLED".equals(slStatus)) {
                        log.info("✅ [BINANCE] Stop Loss Order placed successfully - ID: {} qty={} at ${} (reduceOnly=true)", slOrderId, formattedQty, formattedSlPrice);
                        orderCount++;
                    } else {
                        log.error("❌ [BINANCE] Stop Loss Order failed - Status: {}", slStatus);
                        allSuccess = false;
                    }
                } catch (Exception e) {
                    log.error("❌ [BINANCE] Exception placing Stop Loss Order: {}", e.getMessage());
                    allSuccess = false;
                }
            }

            // Place Take Profit Orders (distribute quantity)
            if (!tpPrices.isEmpty()) {
                for (int i = 0; i < tpPrices.size(); i++) {
                    try {
                        int tpNum = i + 1;
                        log.info("💰 [BINANCE] Placing Take Profit {} Order...", tpNum);

                        // Format TP quantity and stopPrice according to symbol filters
                        double formattedTpQty = formatQuantity(tpQuantities.get(i), symbolFilters);
                        double formattedTpPrice = formatPrice(tpPrices.get(i), symbolFilters);

                        LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
                        tpParams.put("symbol", symbol);
                        tpParams.put("side", closeSide);
                        tpParams.put("type", "TAKE_PROFIT_MARKET");
                        tpParams.put("quantity", formattedTpQty); // CRITICAL: Format quantity
                        tpParams.put("stopPrice", formattedTpPrice); // CRITICAL: Format stopPrice
                        tpParams.put("reduceOnly", "true"); // CRITICAL: Prevent opening new position
                        tpParams.put("recvWindow", 60000);

                        String tpResponse = futuresClient.account().newOrder(tpParams);
                        JSONObject tpResp = new JSONObject(tpResponse);
                        String tpOrderId = tpResp.optString("orderId", "");
                        String tpStatus = tpResp.optString("status", "FAILED");

                        if ("NEW".equals(tpStatus) || "PARTIALLY_FILLED".equals(tpStatus)) {
                            log.info("✅ [BINANCE] Take Profit {} Order placed successfully - ID: {} qty={} at ${} (reduceOnly=true)",
                                tpNum, tpOrderId, formattedTpQty, formattedTpPrice);
                            orderCount++;
                        } else {
                            log.error("❌ [BINANCE] Take Profit {} Order failed - Status: {}", tpNum, tpStatus);
                            allSuccess = false;
                        }
                    } catch (Exception e) {
                        log.error("❌ [BINANCE] Exception placing Take Profit {} Order: {}", i + 1, e.getMessage());
                        allSuccess = false;
                    }
                }
            }

            if (allSuccess) {
                log.info("✅ [BINANCE] All {} orders placed successfully!", orderCount);
                return true;
            } else {
                log.error("❌ [BINANCE] Some orders failed. Check logs for details. Placed {}/{} orders.", orderCount, batchOrders.length());
                return false;
            }

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════════════");
            log.error("❌ [BINANCE] Exception while placing batch orders");
            log.error("❌ [BINANCE] Exception type: {}", e.getClass().getName());
            log.error("❌ [BINANCE] Exception message: {}", e.getMessage());
            log.error("❌ [BINANCE] Full exception: ", e);
            log.error("═══════════════════════════════════════════════════════════════════");
            return false;
        }
    }

    /**
     * Get USDT balance from Binance
     */
    private double getBalance() {
        try {
            log.debug("💰 [BALANCE] Fetching USDT balance from Binance...");
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000);

            String result = futuresClient.account().futuresAccountBalance(params);
            log.debug("📋 [BALANCE] Raw balance response: {}", result);

            var balances = new org.json.JSONArray(result);
            log.debug("💰 [BALANCE] Total balance entries: {}", balances.length());

            for (int i = 0; i < balances.length(); i++) {
                var balance = balances.getJSONObject(i);
                String asset = balance.getString("asset");

                if ("USDT".equals(asset)) {
                    double available = balance.getDouble("availableBalance");
                    log.info("✅ [BALANCE] USDT balance retrieved: ${}", available);
                    return available;
                }
            }

            log.warn("⚠️  [BALANCE] USDT asset not found in balance response");
            return 0.0;

        } catch (Exception e) {
            log.error("❌ [BALANCE] Exception while fetching balance");
            log.error("❌ [BALANCE] Exception type: {}", e.getClass().getName());
            log.error("❌ [BALANCE] Exception message: {}", e.getMessage());
            log.error("❌ [BALANCE] Full exception: ", e);
            return 0.0;
        }
    }

    /**
     * Calculate quantity based on available balance, leverage, and entry price
     * If quantity is provided, use it; otherwise calculate from balance
     */
    private double calculateQuantity(Double requestedQty, int leverage, double balance, double entryPrice) {
        log.debug("📊 [QUANTITY] Calculating order quantity...");
        log.debug("  - Requested quantity: {}", requestedQty);
        log.debug("  - Leverage: {}x", leverage);
        log.debug("  - Available balance: ${}", balance);
        log.debug("  - Entry price: ${}", entryPrice);

        if (requestedQty != null && requestedQty > 0) {
            log.info("📊 [QUANTITY] Using provided quantity: {}", requestedQty);
            return requestedQty; // Use provided quantity
        }

        // Default: Use 25% of available balance MULTIPLIED by leverage, divided by entry price
        // Formula: (Balance * Leverage * Percentage) / EntryPrice
        // This ensures proper notional value for the order with conservative margin usage
        double quantity = (balance * leverage * 0.25) / entryPrice;
        log.info("📊 [QUANTITY] Auto-calculated quantity: {} (25% of ${} * {}x leverage / ${} entry)", quantity, balance, leverage, entryPrice);

        double finalQty = Math.max(quantity, 0.001); // Minimum quantity
        log.debug("📊 [QUANTITY] Final quantity (with minimum): {}", finalQty);

        return finalQty;
    }

    /**
     * Set leverage for symbol
     */
    private void setLeverage(String symbol, int leverage) {
        try {
            log.info("⚙️  [LEVERAGE] Setting leverage for {} to {}x...", symbol, leverage);

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("leverage", leverage);
            params.put("recvWindow", 60000);

            log.debug("⚙️  [LEVERAGE] Sending leverage request: {}", params);
            String response = futuresClient.account().changeInitialLeverage(params);
            log.debug("⚙️  [LEVERAGE] Leverage response: {}", response);

            log.info("✅ [LEVERAGE] Leverage successfully set to {}x for {}", leverage, symbol);

        } catch (Exception e) {
            log.warn("⚠️  [LEVERAGE] Warning while setting leverage");
            log.warn("⚠️  [LEVERAGE] Exception type: {}", e.getClass().getName());
            log.warn("⚠️  [LEVERAGE] Exception message: {}", e.getMessage());
            log.debug("⚠️  [LEVERAGE] Full exception: ", e);
            log.info("⚠️  [LEVERAGE] Continuing with default leverage. This may cause order issues.");
        }
    }

    /**
     * Distribute total quantity evenly across multiple take-profit levels
     * Respects symbol filters (precision) and handles remainders properly
     *
     * Example: 0.5 across 4 TPs with 0.01 step size:
     * Base: 0.5 / 4 = 0.125 → formatted to 0.12
     * TP1-3: 0.12 each
     * TP4 (remainder): 0.5 - (0.12 + 0.12 + 0.12) = 0.14
     * Result: [0.12, 0.12, 0.12, 0.14]
     */
    private List<Double> distributeQuantity(double totalQty, int numLevels, Map<String, Object> symbolFilters) {
        List<Double> quantities = new ArrayList<>();

        if (numLevels == 0) {
            return quantities;
        }

        // Calculate base quantity (unformatted)
        double baseQty = totalQty / numLevels;

        // Format the base quantity according to symbol filters (this rounds DOWN)
        double formattedBaseQty = formatQuantity(baseQty, symbolFilters);

        log.info("📊 [DISTRIBUTION] Distributing {} across {} TP levels (base qty: {} → formatted: {})",
            totalQty, numLevels, baseQty, formattedBaseQty);

        // Distribute formatted base quantity to first N-1 TPs
        double accumulatedQty = 0;
        for (int i = 0; i < numLevels - 1; i++) {
            quantities.add(formattedBaseQty);
            accumulatedQty += formattedBaseQty;
            log.debug("  - TP{}: {} (accumulated: {})", i + 1, formattedBaseQty, accumulatedQty);
        }

        // Calculate remainder for last TP (ensures total equals input qty)
        double remainderQty = totalQty - accumulatedQty;
        double formattedRemainderQty = formatQuantity(remainderQty, symbolFilters);
        quantities.add(formattedRemainderQty);

        log.debug("  - TP{} (remainder): {} (before format: {})", numLevels, formattedRemainderQty, remainderQty);
        log.info("✅ [DISTRIBUTION] Distribution complete: {} (sum: {})", quantities,
            quantities.stream().mapToDouble(Double::doubleValue).sum());

        return quantities;
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

    /**
     * Fetch exchange info and get symbol filters (LOT_SIZE, PRICE_FILTER)
     * This is CRITICAL for placing orders with correct precision
     */
    private Map<String, Object> getSymbolFilters(String symbol) {
        try {
            log.info("🔍 [FILTERS] Fetching exchange info for symbol: {}", symbol);

            log.debug("🔍 [FILTERS] Calling Binance exchangeInfo endpoint...");
            String infoResponse = futuresClient.market().exchangeInfo();
            log.debug("🔍 [FILTERS] Raw exchange info response (length: {})", infoResponse.length());

            JSONObject info = new JSONObject(infoResponse);
            JSONArray symbols = info.getJSONArray("symbols");

            log.debug("🔍 [FILTERS] Total symbols in response: {}", symbols.length());

            for (int i = 0; i < symbols.length(); i++) {
                JSONObject s = symbols.getJSONObject(i);
                String symName = s.getString("symbol");

                if (symbol.equals(symName)) {
                    log.info("✅ [FILTERS] Found symbol: {}", symbol);

                    JSONArray filters = s.getJSONArray("filters");
                    Map<String, Object> filterMap = new HashMap<>();

                    log.debug("🔍 [FILTERS] Processing {} filters for {}", filters.length(), symbol);

                    for (int j = 0; j < filters.length(); j++) {
                        JSONObject f = filters.getJSONObject(j);
                        String filterType = f.getString("filterType");
                        filterMap.put(filterType, f);

                        // Log the important filters
                        if ("LOT_SIZE".equals(filterType)) {
                            log.info("📦 [FILTERS] LOT_SIZE (Step Size): {}", f.getString("stepSize"));
                            log.info("  - Min Qty: {}", f.getString("minQty"));
                            log.info("  - Max Qty: {}", f.getString("maxQty"));
                        }
                        if ("PRICE_FILTER".equals(filterType)) {
                            log.info("💰 [FILTERS] PRICE_FILTER (Tick Size): {}", f.getString("tickSize"));
                            log.info("  - Min Price: {}", f.getString("minPrice"));
                            log.info("  - Max Price: {}", f.getString("maxPrice"));
                        }
                    }

                    log.info("✅ [FILTERS] Successfully fetched filters: {}", filterMap.keySet());
                    return filterMap;
                }
            }

            log.error("❌ [FILTERS] Symbol {} not found in Binance exchange info", symbol);
            return null;

        } catch (Exception e) {
            log.error("═══════════════════════════════════════════════════════════════════");
            log.error("❌ [FILTERS] Exception while fetching symbol filters");
            log.error("❌ [FILTERS] Exception type: {}", e.getClass().getName());
            log.error("❌ [FILTERS] Exception message: {}", e.getMessage());
            log.error("❌ [FILTERS] Full exception: ", e);
            log.error("═══════════════════════════════════════════════════════════════════");
            return null;
        }
    }

    /**
     * Format quantity based on LOT_SIZE filter
     * Example: stepSize "0.01" → allows 2 decimal places
     */
    private double formatQuantity(double quantity, Map<String, Object> filters) {
        try {
            log.debug("📊 [QUANTITY] Formatting quantity: {}", quantity);

            if (filters == null) {
                log.warn("⚠️  [QUANTITY] No filters provided, using raw quantity");
                return quantity;
            }

            JSONObject lotSize = (JSONObject) filters.get("LOT_SIZE");
            if (lotSize == null) {
                log.warn("⚠️  [QUANTITY] LOT_SIZE filter not found, using raw quantity");
                return quantity;
            }

            String stepSize = lotSize.getString("stepSize");
            log.debug("📊 [QUANTITY] Step size from filter: {}", stepSize);

            int decimals = getDecimals(stepSize);
            log.debug("📊 [QUANTITY] Calculated decimal places: {}", decimals);

            // Use BigDecimal for precise rounding
            BigDecimal bd = new BigDecimal(quantity);
            // Use DOWN (floor) to avoid oversizing the order while staying within precision
            bd = bd.setScale(decimals, RoundingMode.DOWN);
            double formatted = bd.doubleValue();

            log.info("✅ [QUANTITY] Formatted: {} → {} (step size: {}, decimals: {})",
                quantity, formatted, stepSize, decimals);

            return formatted;

        } catch (Exception e) {
            log.error("❌ [QUANTITY] Error formatting quantity: {}", e.getMessage());
            log.debug("❌ [QUANTITY] Using raw quantity as fallback");
            return quantity;
        }
    }

    /**
     * Format price based on PRICE_FILTER
     * Example: tickSize "0.01" → allows 2 decimal places
     */
    private double formatPrice(double price, Map<String, Object> filters) {
        try {
            log.debug("💰 [PRICE] Formatting price: ${}", price);

            if (filters == null) {
                log.warn("⚠️  [PRICE] No filters provided, using raw price");
                return price;
            }

            JSONObject priceFilter = (JSONObject) filters.get("PRICE_FILTER");
            if (priceFilter == null) {
                log.warn("⚠️  [PRICE] PRICE_FILTER not found, using raw price");
                return price;
            }

            String tickSize = priceFilter.getString("tickSize");
            log.debug("💰 [PRICE] Tick size from filter: {}", tickSize);

            int decimals = getDecimals(tickSize);
            log.debug("💰 [PRICE] Calculated decimal places: {}", decimals);

            // Use BigDecimal for precise rounding
            BigDecimal bd = new BigDecimal(price);
            bd = bd.setScale(decimals, RoundingMode.HALF_UP);  // Standard rounding for price
            double formatted = bd.doubleValue();

            log.info("✅ [PRICE] Formatted: ${} → ${} (tick size: {}, decimals: {})",
                price, formatted, tickSize, decimals);

            return formatted;

        } catch (Exception e) {
            log.error("❌ [PRICE] Error formatting price: {}", e.getMessage());
            log.debug("❌ [PRICE] Using raw price as fallback");
            return price;
        }
    }

    /**
     * Helper: Extract decimal places from filter value
     * Examples: "0.01" → 2, "0.1" → 1, "0.001" → 3, "1" → 0
     */
    private int getDecimals(String filterValue) {
        try {
            if (filterValue == null || filterValue.isEmpty()) {
                log.warn("⚠️  [DECIMALS] Empty filter value, defaulting to 0 decimals");
                return 0;
            }

            String[] parts = filterValue.split("\\.");

            if (parts.length == 1) {
                // No decimal point, e.g., "1"
                log.debug("🔢 [DECIMALS] No decimal point in: {}, returning 0 decimals", filterValue);
                return 0;
            } else {
                // Has decimal point, count digits after it
                int decimals = parts[1].length();
                log.debug("🔢 [DECIMALS] Found {} decimal places in: {}", decimals, filterValue);
                return decimals;
            }

        } catch (Exception e) {
            log.error("❌ [DECIMALS] Error parsing decimals from: {}", filterValue);
            return 0;  // Default to 0 decimals if error
        }
    }
}
