package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinancePositionDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinanceOrderDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;

@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    @Autowired
    private TradeManagementConfigService tradeManagementConfigService;

    /**
     * Execute a new trade based on the request
     */
    public ExecuteTradeResponse executeTrade(ExecuteTradeRequest request) {
        try {
            // Validate request
            if (request.getPair() == null || request.getSide() == null || request.getEntry() == null) {
                return new ExecuteTradeResponse(null, request.getPair(), "FAILED", "Missing required fields");
            }

            // ✅ VALIDATE AGAINST TRADE MANAGEMENT CONFIG (Position Size only)
            Long userId = request.getUserId();
            if (userId != null && !tradeManagementConfigService.isTradeValid(userId, request)) {
                String validationError = tradeManagementConfigService.getValidationError(userId, request);
                log.warn("❌ Trade validation FAILED: {}", validationError);
                return new ExecuteTradeResponse(null, request.getPair(), "FAILED", validationError);
            }

            log.info("🚀 Executing trade: {} {} @ ${}", request.getSide(), request.getPair(), request.getEntry());

            // 1. Create Trade record in database
            Trade trade = new Trade();
            trade.setPair(request.getPair());
            trade.setSide(request.getSide());
            trade.setEntryPrice(request.getEntry());
            trade.setEntryQuantity(request.getQuantity() != null ? request.getQuantity() : 0.0);

            // ✅ CAP LEVERAGE IF EXCEEDS MAX
            int originalLeverage = request.getLeverage() != null ? request.getLeverage() : 1;
            int cappedLeverage = originalLeverage;
            if (userId != null) {
                TradeManagementConfig config = tradeManagementConfigService.getActiveConfig(userId);
                int maxLeverage = config.getMaxLeverage().intValue();
                if (originalLeverage > maxLeverage) {
                    log.info("⚠️ Leverage capped: {}x → {}x", originalLeverage, maxLeverage);
                    cappedLeverage = maxLeverage;
                }
            }
            trade.setLeverage(cappedLeverage);

            trade.setStopLoss(request.getStopLoss());
            trade.setTp1(request.getTp1());
            trade.setTp2(request.getTp2());
            trade.setTp3(request.getTp3());
            trade.setTp4(request.getTp4());
            trade.setStatus("PENDING");
            trade.setOpenedAt(OffsetDateTime.now());
            trade.setSignalId(request.getSignalId());
            trade.setUserId(userId);

            // Save initial trade record
            Trade savedTrade = tradeRepository.save(trade);
            log.info("✅ Trade record created with ID: {}", savedTrade.getId());

            // 2. Place order on Binance
            if (futuresClient != null) {
                boolean orderPlaced = placeBinanceOrder(savedTrade, userId);
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
     * ✅ UPDATED: Now applies position quantity splitting based on TP exit percentages
     */
    private boolean placeBinanceOrder(Trade trade, Long userId) {
        try {
            double balance = getBalance();
            if (balance <= 10) {
                log.error("❌ Insufficient balance. USDT={}", balance);
                return false;
            }

            // ✅ STRIP .P SUFFIX FROM SYMBOL (Telegram notation → Binance format)
            String symbol = trade.getPair();
            if (symbol != null && symbol.endsWith(".P")) {
                symbol = symbol.substring(0, symbol.length() - 2);
                log.info("🔄 Converted symbol: {} → {} (removed .P suffix)", trade.getPair(), symbol);
            }

            // ✅ The side is already converted by the controller (LONG→BUY, SHORT→SELL)
            // Just use it as-is, don't convert again
            String side = trade.getSide();

            // ✅ GET USER'S TRADE MANAGEMENT CONFIG
            TradeManagementConfig config = null;
            if (userId != null) {
                config = tradeManagementConfigService.getActiveConfig(userId);
                log.info("📋 Applying config for user {}: MarginMode={}, TP:{}%/{}%/{}%/{}%",
                    userId,
                    config.getMarginMode(),
                    config.getTp1ExitPercentage(),
                    config.getTp2ExitPercentage(),
                    config.getTp3ExitPercentage(),
                    config.getTp4ExitPercentage()
                );
            }

            // 1. Set leverage
            setLeverage(symbol, trade.getLeverage());

            // 2. Set margin mode if config available
            if (config != null) {
                setMarginMode(symbol, config.getMarginMode());
            }

            // 2. Place LIMIT order at entry price
            double entryQty = calculateQuantity(trade.getEntryQuantity(), trade.getEntryPrice(), balance);

            // ✅ FETCH DYNAMIC FILTERS FROM BINANCE API (LOT_SIZE, MIN_NOTIONAL, precision)
            SymbolFilters filters = getSymbolFilters(symbol);
            double stepSize = filters.lotSize;
            double minNotional = filters.minNotional;

            // ✅ ROUND QUANTITY TO PROPER DECIMAL PLACES
            double roundedEntryQty = roundQuantityToDecimal(entryQty, filters.quantityPrecision);

            // ✅ ADJUST TO STEP SIZE (Binance requires quantities as multiples of step size)
            String finalQty = adjustQuantityToStepSize(roundedEntryQty, stepSize);

            // ⚠️ VALIDATE MIN_NOTIONAL: Order value must meet minimum requirement
            double orderValue = Double.parseDouble(finalQty) * trade.getEntryPrice();
            if (orderValue < minNotional) {
                log.error("❌ Order value ${} is below MIN_NOTIONAL ${}", orderValue, minNotional);
                return false;
            }

            double roundedPrice = roundPrice(trade.getEntryPrice()); // ✅ ROUND PRICE

            LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
            orderParams.put("symbol", symbol);
            orderParams.put("side", side);
            orderParams.put("type", "LIMIT");
            orderParams.put("timeInForce", "GTC"); // Good Till Cancel
            orderParams.put("quantity", finalQty); // ✅ STEP SIZE ADJUSTED
            orderParams.put("price", roundedPrice); // ✅ LIMIT order with rounded price
            orderParams.put("recvWindow", 60000);

            log.info("📍 Placing LIMIT order: {} {} @ ${} Qty={} (original: {})",
                side, symbol, roundedPrice, finalQty, entryQty);

            String orderResponse = futuresClient.account().newOrder(orderParams);
            JSONObject resp = new JSONObject(orderResponse);
            String orderId = resp.optString("orderId", "");
            String status = resp.optString("status", "FAILED");

            // ⚠️ CRITICAL FIX: For LIMIT orders, executedQty is 0 (order hasn't filled yet)
            // Use origQty (original quantity requested) instead
            double executedQty;
            if ("NEW".equals(status) || "PARTIALLY_FILLED".equals(status)) {
                // For pending LIMIT orders, use the original quantity we requested
                executedQty = resp.optDouble("origQty", 0.0);
                log.info("📊 LIMIT Order (NEW): Using origQty for SL/TP calculations");
            } else {
                // For fully filled orders, use executedQty
                executedQty = resp.optDouble("executedQty", 0.0);
            }

            log.info("✅ LIMIT Order placed: {} {} Qty={} Status={}", side, symbol, executedQty, status);

            trade.setBinanceOrderId(orderId);

            // 3. Place Stop-Loss
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                double roundedSlQty = roundQuantityToDecimal(executedQty, filters.quantityPrecision); // ✅ ROUND SL QUANTITY
                placeStopLoss(symbol, side, roundedSlQty, trade.getStopLoss());
            }

            // ✅ 4. PLACE TAKE-PROFITS WITH QUANTITY SPLITTING
            if (config != null) {
                // Calculate quantities based on TP exit percentages
                double tp1Qty = executedQty * (config.getTp1ExitPercentage().doubleValue() / 100.0);
                double tp2Qty = executedQty * (config.getTp2ExitPercentage().doubleValue() / 100.0);
                double tp3Qty = executedQty * (config.getTp3ExitPercentage().doubleValue() / 100.0);
                double tp4Qty = executedQty * (config.getTp4ExitPercentage().doubleValue() / 100.0);

                // ✅ ROUND QUANTITIES TO PROPER DECIMAL PLACES (using dynamic precision from filters)
                double tp1QtyRounded = roundQuantityToDecimal(tp1Qty, filters.quantityPrecision);
                double tp2QtyRounded = roundQuantityToDecimal(tp2Qty, filters.quantityPrecision);
                double tp3QtyRounded = roundQuantityToDecimal(tp3Qty, filters.quantityPrecision);
                double tp4QtyRounded = roundQuantityToDecimal(tp4Qty, filters.quantityPrecision);

                // ✅ ADJUST TO STEP SIZE (using dynamic step size from filters)
                String tp1QtyAdjusted = adjustQuantityToStepSize(tp1QtyRounded, filters.lotSize);
                String tp2QtyAdjusted = adjustQuantityToStepSize(tp2QtyRounded, filters.lotSize);
                String tp3QtyAdjusted = adjustQuantityToStepSize(tp3QtyRounded, filters.lotSize);
                String tp4QtyAdjusted = adjustQuantityToStepSize(tp4QtyRounded, filters.lotSize);

                log.info("📊 Position Quantity Split: TP1={}({}%), TP2={}({}%), TP3={}({}%), TP4={}({}%)",
                    tp1QtyAdjusted, config.getTp1ExitPercentage(),
                    tp2QtyAdjusted, config.getTp2ExitPercentage(),
                    tp3QtyAdjusted, config.getTp3ExitPercentage(),
                    tp4QtyAdjusted, config.getTp4ExitPercentage()
                );

                // Place TP orders with SIGNAL TP PRICES (unchanged) and STEP-SIZE-ADJUSTED QUANTITIES
                if (trade.getTp1() != null && trade.getTp1() > 0 && Double.parseDouble(tp1QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp1QtyAdjusted), trade.getTp1(), "TP1");
                }
                if (trade.getTp2() != null && trade.getTp2() > 0 && Double.parseDouble(tp2QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp2QtyAdjusted), trade.getTp2(), "TP2");
                }
                if (trade.getTp3() != null && trade.getTp3() > 0 && Double.parseDouble(tp3QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp3QtyAdjusted), trade.getTp3(), "TP3");
                }
                if (trade.getTp4() != null && trade.getTp4() > 0 && Double.parseDouble(tp4QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp4QtyAdjusted), trade.getTp4(), "TP4");
                }
            } else {
                // Fallback: Place all TP orders with full quantity (if no config)
                log.warn("⚠️ No user config found - placing TPs with full quantity");
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
            }

            log.info("✅ All orders placed for {}", symbol);
            return true;

        } catch (Exception e) {
            log.error("❌ Error placing Binance order: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper class to store exchange info filters for a symbol
     */
    private static class SymbolFilters {
        double lotSize;      // Step size for quantity
        double minNotional;  // Minimum order value
        int quantityPrecision; // Decimal places for quantity

        SymbolFilters(double lotSize, double minNotional, int quantityPrecision) {
            this.lotSize = lotSize;
            this.minNotional = minNotional;
            this.quantityPrecision = quantityPrecision;
        }
    }

    /**
     * 🔄 Fetch symbol filters from Binance ExchangeInfo API
     * Returns LOT_SIZE (step size), MIN_NOTIONAL, and quantity precision
     * Required by Binance FAPI: /fapi/v1/exchangeInfo
     */
    private SymbolFilters getSymbolFilters(String symbol) {
        try {
            log.info("🔍 Fetching exchange info for symbol: {}", symbol);
            String exchangeInfo = futuresClient.market().exchangeInfo();

            JSONObject response = new JSONObject(exchangeInfo);
            JSONArray symbols = response.getJSONArray("symbols");

            for (int i = 0; i < symbols.length(); i++) {
                JSONObject symbolObj = symbols.getJSONObject(i);
                if (symbol.equals(symbolObj.getString("symbol"))) {
                    JSONArray filters = symbolObj.getJSONArray("filters");
                    double lotSize = 1.0;
                    double minNotional = 10.0;
                    int quantityPrecision = 2;

                    for (int j = 0; j < filters.length(); j++) {
                        JSONObject filter = filters.getJSONObject(j);
                        String filterType = filter.getString("filterType");

                        // Extract LOT_SIZE filter
                        if ("LOT_SIZE".equals(filterType)) {
                            lotSize = filter.getDouble("stepSize");
                            quantityPrecision = getDecimalPlaces(filter.getString("stepSize"));
                            log.info("📏 LOT_SIZE filter: stepSize={}, precision={}", lotSize, quantityPrecision);
                        }

                        // Extract MIN_NOTIONAL filter
                        if ("MIN_NOTIONAL".equals(filterType)) {
                            minNotional = filter.getDouble("notional");
                            log.info("💰 MIN_NOTIONAL filter: {}", minNotional);
                        }
                    }

                    log.info("✅ Symbol filters for {}: lotSize={}, minNotional={}, precision={}",
                        symbol, lotSize, minNotional, quantityPrecision);
                    return new SymbolFilters(lotSize, minNotional, quantityPrecision);
                }
            }

            log.warn("⚠️ Symbol {} not found in exchange info, using defaults", symbol);
            return new SymbolFilters(1.0, 10.0, 2);

        } catch (Exception e) {
            log.warn("⚠️ Error fetching exchange info for {}: {}", symbol, e.getMessage());
            return new SymbolFilters(1.0, 10.0, 2); // Default filters
        }
    }

    /**
     * Calculate decimal places from step size string
     * e.g., "0.1" → 1, "0.01" → 2, "1" → 0
     */
    private int getDecimalPlaces(String stepSize) {
        String[] parts = stepSize.split("\\.");
        if (parts.length == 2) {
            return parts[1].length();
        }
        return 0;
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
     * Calculate quantity based on available balance and entry price
     * If quantity is provided, use it; otherwise calculate from balance
     * Formula: Quantity = (Balance × 0.5) ÷ EntryPrice
     * This ensures the position size is 50% of available balance
     */
    private double calculateQuantity(Double requestedQty, double entryPrice, double balance) {
        if (requestedQty != null && requestedQty > 0) {
            return requestedQty; // Use provided quantity
        }
        // Default: Use 50% of available balance divided by entry price
        // This gives us the quantity that will cost 50% of our balance
        double quantity = (balance * 0.5) / entryPrice;
        log.info("📊 Auto-calculated quantity: {} (50% of balance: ${} ÷ price: ${})", quantity, balance * 0.5, entryPrice);
        return Math.max(quantity, 0.001); // Minimum quantity
    }

    /**
     * Round quantity to appropriate decimal places for asset
     * SOL uses 2 decimal places max on Binance Futures
     */
    private double roundQuantity(double quantity) {
        // Round to 2 decimal places (SOL requires less precision)
        return Math.round(quantity * 100.0) / 100.0;
    }

    /**
     * 🔧 Round quantity to specific number of decimal places
     * Uses dynamic precision from Binance filters (LOT_SIZE)
     * Examples: precision=0 → rounds to whole number, precision=2 → 2 decimal places
     */
    private double roundQuantityToDecimal(double quantity, int decimalPlaces) {
        if (decimalPlaces < 0) decimalPlaces = 0;
        double multiplier = Math.pow(10, decimalPlaces);
        double rounded = Math.round(quantity * multiplier) / multiplier;
        log.info("📐 Rounded quantity: {} → {} (decimal places: {})", quantity, rounded, decimalPlaces);
        return rounded;
    }

    /**
     * Round price to appropriate decimal places
     * SOL typically uses 2 decimal places
     */
    private double roundPrice(double price) {
        return Math.round(price * 100.0) / 100.0;
    }

    /**
     * ✅ BINANCE FAPI COMPLIANT: Adjust quantity to match asset's step size
     * According to Binance documentation, quantities must be multiples of LOT_SIZE (stepSize)
     * This method:
     * 1. Rounds DOWN to the nearest step size multiple
     * 2. Removes trailing zeros to prevent precision artifacts
     *
     * Example: rawQuantity=0.575, stepSize=0.01 → "0.57"
     * NOT "0.57000000000000001186..." ✅
     */
    private String adjustQuantityToStepSize(double rawQuantity, double stepSize) {
        // Use String constructor to avoid floating-point precision issues from the start
        BigDecimal qty = new BigDecimal(String.valueOf(rawQuantity));
        BigDecimal step = new BigDecimal(String.valueOf(stepSize));

        // 1. Divide by step size and round DOWN (e.g., 0.575 / 0.01 = 57.5 → 57)
        BigDecimal value = qty.divide(step, 0, RoundingMode.DOWN);

        // 2. Multiply back by step size (57 * 0.01 = 0.57)
        BigDecimal adjusted = value.multiply(step);

        // 3. Set scale to match step size's decimal places, then strip trailing zeros
        // This ensures "0.57" NOT "0.57000000000000001186..."
        int decimalPlaces = getDecimalPlaces(String.valueOf(stepSize));
        String result = adjusted
            .setScale(decimalPlaces, RoundingMode.DOWN)  // ⚠️ CRITICAL: Force exact decimal places
            .stripTrailingZeros()                         // Remove "0.57000" → "0.57"
            .toPlainString();                             // Convert to string without scientific notation

        log.info("📏 Quantity Adjusted: {} → {} (Step Size: {})", rawQuantity, result, stepSize);
        return result;
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
     * Set margin mode for symbol (ISOLATE or CROSS)
     * According to Binance FAPI documentation:
     * - Parameter name must be: "marginType" (capital T)
     * - Valid values: "ISOLATED" or "CROSS"
     */
    private void setMarginMode(String symbol, String marginMode) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("marginType", marginMode); // ✅ BINANCE FAPI: Must be "marginType" (case-sensitive)
            params.put("recvWindow", 60000);

            log.info("🔧 Setting margin mode: symbol={}, marginType={}", symbol, marginMode);
            futuresClient.account().changeMarginType(params);
            log.info("✅ Margin mode successfully set to {} for {}", marginMode, symbol);
        } catch (Exception e) {
            // Note: This may fail if margin type is already set to the requested value
            log.warn("⚠️ Error setting margin mode (marginType={}): {}", marginMode, e.getMessage());
        }
    }

    /**
     * Place Stop-Loss order
     * ✅ UPDATED: Added reduce_only=true to allow orders below MIN_NOTIONAL
     * Binance allows orders < $5 notional if reduce_only=true (closing positions)
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
            slParams.put("reduceOnly", true);  // ✅ NEW: Allow orders < $5 notional
            slParams.put("recvWindow", 60000);

            String resp = futuresClient.account().newOrder(slParams);
            JSONObject respObj = new JSONObject(resp);
            String orderId = respObj.optString("orderId", "");
            log.info("🛑 Stop-Loss placed: orderId={}", orderId);
            return true;
        } catch (Exception e) {
            log.error("❌ Error placing Stop-Loss: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Place Take-Profit order
     * ✅ UPDATED: Added reduce_only=true and MIN_NOTIONAL validation
     * Binance allows orders < $5 notional if reduce_only=true (closing positions)
     *
     * @param symbol Trading pair (e.g., SOLUSDT)
     * @param side BUY or SELL (entry side, TP side is opposite)
     * @param qty Quantity to close at this TP level
     * @param tpPrice Take-profit price from signal (static)
     * @param label TP level label (TP1, TP2, TP3, TP4)
     */
    private void placeTakeProfit(String symbol, String side, double qty, double tpPrice, String label) {
        try {
            // ✅ MIN_NOTIONAL VALIDATION: Check if order meets Binance minimum notional
            double tpNotional = qty * tpPrice;
            if (tpNotional < 5.0) {
                log.warn("⚠️ {} order notional (${}) is below Binance minimum ($5). Will use reduce_only=true", label, tpNotional);
            }

            String tpSide = side.equals("BUY") ? "SELL" : "BUY";
            LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
            tpParams.put("symbol", symbol);
            tpParams.put("side", tpSide);
            tpParams.put("type", "TAKE_PROFIT_MARKET");
            tpParams.put("quantity", qty);
            tpParams.put("stopPrice", tpPrice);
            tpParams.put("timeInForce", "GTC");
            tpParams.put("reduceOnly", true);  // ✅ NEW: Allow orders < $5 notional
            tpParams.put("recvWindow", 60000);

            String resp = futuresClient.account().newOrder(tpParams);
            JSONObject respObj = new JSONObject(resp);
            String orderId = respObj.optString("orderId", "");
            String status = respObj.optString("status", "FAILED");

            if ("NEW".equals(status) || "PARTIALLY_FILLED".equals(status)) {
                log.info("📈 {} placed: orderId={}, notional=${}", label, orderId, tpNotional);
            } else {
                log.warn("⚠️ {} order status: {}", label, status);
            }
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

    // ============ BINANCE OPEN POSITIONS ============

    /**
     * ✅ NEW: Fetch all open positions from Binance Futures
     *
     * Retrieves:
     * - Current open positions with P&L calculations
     * - Mark prices for real-time P&L updates
     * - Related open orders (entry, TP, SL)
     *
     * @return List of open positions from Binance
     */
    public List<BinancePositionDTO> getOpenPositionsFromBinance() {
        List<BinancePositionDTO> positions = new ArrayList<>();

        try {
            log.info("📊 Fetching open positions from Binance...");

            // Get all open positions with unrealized P&L
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000);
            String positionsResponse = futuresClient.account().positionRisk(params);
            JSONArray positionsArray = new JSONArray(positionsResponse);

            // Get all open orders to match with positions
            String ordersResponse = futuresClient.account().openOrders(params);
            JSONArray ordersArray = new JSONArray(ordersResponse);

            for (int i = 0; i < positionsArray.length(); i++) {
                JSONObject posObj = positionsArray.getJSONObject(i);

                // Skip positions with 0 quantity (closed)
                double positionAmt = posObj.getDouble("positionAmt");
                if (positionAmt == 0) {
                    continue;
                }

                String symbol = posObj.getString("symbol");

                // Get current mark price for this symbol
                BigDecimal markPrice = getMarkPrice(symbol);

                // Build position DTO
                BinancePositionDTO position = BinancePositionDTO.builder()
                    .symbol(symbol)
                    .side(positionAmt > 0 ? "LONG" : "SHORT")
                    .entryPrice(new BigDecimal(posObj.getString("entryPrice")))
                    .markPrice(markPrice)
                    .quantity(new BigDecimal(String.format("%.8f", Math.abs(positionAmt))))
                    .leverage(posObj.getInt("leverage"))
                    .unrealizedPnL(new BigDecimal(posObj.getString("unRealizedProfit")))
                    .unrealizedPnLPct(new BigDecimal(posObj.getString("percentage")).multiply(BigDecimal.valueOf(100)))
                    .liquidationPrice(new BigDecimal(posObj.getString("liquidationPrice")))
                    .marginType(posObj.getString("marginType").toLowerCase())
                    .isReduceOnly(posObj.getBoolean("reduceOnly"))
                    .openedAt(System.currentTimeMillis())
                    .openOrders(getOpenOrdersForSymbol(ordersArray, symbol))
                    .build();

                positions.add(position);
                log.info("✅ Position fetched: {} {} @ ${} Qty={} P&L=${} ({}%)",
                    position.getSide(), symbol, position.getMarkPrice(),
                    position.getQuantity(), position.getUnrealizedPnL(),
                    position.getUnrealizedPnLPct());
            }

            log.info("📊 Total open positions: {}", positions.size());
            return positions;

        } catch (Exception e) {
            log.error("❌ Error fetching open positions from Binance: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get mark price for a symbol from Binance
     */
    private BigDecimal getMarkPrice(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("recvWindow", 60000);
            String response = futuresClient.market().ticker(params);
            JSONObject ticker = new JSONObject(response);
            return new BigDecimal(ticker.getString("markPrice"));
        } catch (Exception e) {
            log.warn("⚠️ Could not fetch mark price for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get all open orders for a specific symbol
     */
    private List<BinanceOrderDTO> getOpenOrdersForSymbol(JSONArray allOrders, String symbol) {
        List<BinanceOrderDTO> symbolOrders = new ArrayList<>();

        try {
            for (int i = 0; i < allOrders.length(); i++) {
                JSONObject orderObj = allOrders.getJSONObject(i);

                if (!symbol.equals(orderObj.getString("symbol"))) {
                    continue; // Skip orders for different symbols
                }

                BinanceOrderDTO order = BinanceOrderDTO.builder()
                    .orderId(orderObj.getString("orderId"))
                    .clientOrderId(orderObj.getString("clientOrderId"))
                    .symbol(symbol)
                    .side(orderObj.getString("side"))
                    .type(orderObj.getString("type"))
                    .price(new BigDecimal(orderObj.getString("price")))
                    .stopPrice(new BigDecimal(orderObj.optString("stopPrice", "0")))
                    .quantity(new BigDecimal(orderObj.getString("origQty")))
                    .executedQuantity(new BigDecimal(orderObj.getString("executedQty")))
                    .status(orderObj.getString("status"))
                    .timeInForce(orderObj.getString("timeInForce"))
                    .reduceOnly(orderObj.getBoolean("reduceOnly"))
                    .createTime(orderObj.getLong("time"))
                    .updateTime(orderObj.getLong("updateTime"))
                    .build();

                // Set label based on order type
                if ("LIMIT".equals(order.getType()) && order.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                    order.setOrderLabel("ENTRY");
                } else if ("STOP_MARKET".equals(order.getType())) {
                    order.setOrderLabel("SL");
                } else if ("TAKE_PROFIT_MARKET".equals(order.getType())) {
                    order.setOrderLabel("TP");
                }

                symbolOrders.add(order);
            }
        } catch (Exception e) {
            log.warn("⚠️ Error parsing orders for {}: {}", symbol, e.getMessage());
        }

        return symbolOrders;
    }

    /**
     * ✅ NEW: Close position on Binance with MARKET order
     *
     * Uses reduceOnly=true to liquidate existing position
     *
     * @param symbol Trading pair (e.g., SOLUSDT)
     * @param side Current position side (LONG or SHORT)
     * @param quantity Quantity to close
     * @return True if order placed successfully
     */
    public boolean closePositionOnBinanceMarket(String symbol, String side, double quantity) {
        try {
            // Determine close side: opposite of current position
            String closeSide = "LONG".equalsIgnoreCase(side) ? "SELL" : "BUY";

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("side", closeSide);
            params.put("type", "MARKET");
            params.put("quantity", quantity);
            params.put("reduceOnly", true);  // ✅ Close position only
            params.put("recvWindow", 60000);

            log.info("📉 Closing position: {} {} Qty={}...", closeSide, symbol, quantity);
            String resp = futuresClient.account().newOrder(params);

            JSONObject respObj = new JSONObject(resp);
            String orderId = respObj.optString("orderId", "");
            String status = respObj.optString("status", "FAILED");

            if ("FILLED".equals(status)) {
                log.info("✅ Position closed successfully: orderId={}", orderId);
                return true;
            } else if ("PARTIALLY_FILLED".equals(status)) {
                log.warn("⚠️ Position partially closed: orderId={}", orderId);
                return true;
            } else {
                log.error("❌ Failed to close position: {}", status);
                return false;
            }

        } catch (Exception e) {
            log.error("❌ Error closing position on Binance: {}", e.getMessage());
            return false;
        }
    }
}
