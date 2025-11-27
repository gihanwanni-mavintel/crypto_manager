package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinancePositionDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinanceOrderDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.TradeFilterDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.TradeHistoryResponseDTO;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
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

            // ✅ LOG TRADE MANAGEMENT CONFIG (will cap values, not reject)
            Long userId = request.getUserId();
            if (userId != null) {
                TradeManagementConfig config = tradeManagementConfigService.getActiveConfig(userId);
                BigDecimal tradeAmount = BigDecimal.valueOf(request.getAmount() != null ? request.getAmount() : 0);
                BigDecimal tradeLeverage = BigDecimal.valueOf(request.getLeverage() != null ? request.getLeverage() : 1);

                if (tradeAmount.compareTo(config.getMaxPositionSize()) > 0) {
                    log.info("⚠️ Position size ${} exceeds max ${}, will be CAPPED", tradeAmount, config.getMaxPositionSize());
                } else if (tradeLeverage.compareTo(config.getMaxLeverage()) > 0) {
                    log.info("⚠️ Leverage {}x exceeds max {}x, will be CAPPED", tradeLeverage, config.getMaxLeverage());
                }
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

            // ✅ CAP POSITION SIZE TO TRADE MANAGEMENT MAXIMUM
            if (config != null) {
                double positionValue = entryQty * trade.getEntryPrice();
                double maxPositionSize = config.getMaxPositionSize().doubleValue();
                if (positionValue > maxPositionSize) {
                    double cappedQty = maxPositionSize / trade.getEntryPrice();
                    log.info("⚠️ Position size capped: ${} → ${} | Qty: {} → {}",
                        positionValue, maxPositionSize, entryQty, cappedQty);
                    entryQty = cappedQty;
                }
            }

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

            double roundedPrice = roundPrice(trade.getEntryPrice(), filters.pricePrecision); // ✅ ROUND PRICE WITH DYNAMIC PRECISION

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
                placeStopLoss(symbol, side, roundedSlQty, trade.getStopLoss(), filters.pricePrecision);
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
                    tp1QtyAdjusted, config.getTp1Percentage(),
                    tp2QtyAdjusted, config.getTp2Percentage(),
                    tp3QtyAdjusted, config.getTp3Percentage(),
                    tp4QtyAdjusted, config.getTp4Percentage()
                );

                // ✅ USE TP PRICES FROM SIGNAL (DO NOT RECALCULATE)
                // TP percentages are for POSITION SIZING only, not for price calculation
                double tp1Price = trade.getTp1() != null && trade.getTp1() > 0 ? trade.getTp1() : 0;
                double tp2Price = trade.getTp2() != null && trade.getTp2() > 0 ? trade.getTp2() : 0;
                double tp3Price = trade.getTp3() != null && trade.getTp3() > 0 ? trade.getTp3() : 0;
                double tp4Price = trade.getTp4() != null && trade.getTp4() > 0 ? trade.getTp4() : 0;

                log.info("📈 TP Prices from signal: TP1=${}, TP2=${}, TP3=${}, TP4=${} | Position sizing: TP1={}%, TP2={}%, TP3={}%, TP4={}%",
                    tp1Price, tp2Price, tp3Price, tp4Price,
                    config.getTp1Percentage(), config.getTp2Percentage(),
                    config.getTp3Percentage(), config.getTp4Percentage());

                // Place TP orders with CALCULATED PRICES and STEP-SIZE-ADJUSTED QUANTITIES
                if (Double.parseDouble(tp1QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp1QtyAdjusted), tp1Price, "TP1", filters.pricePrecision);
                }
                if (Double.parseDouble(tp2QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp2QtyAdjusted), tp2Price, "TP2", filters.pricePrecision);
                }
                if (Double.parseDouble(tp3QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp3QtyAdjusted), tp3Price, "TP3", filters.pricePrecision);
                }
                if (Double.parseDouble(tp4QtyAdjusted) > 0) {
                    placeTakeProfit(symbol, side, Double.parseDouble(tp4QtyAdjusted), tp4Price, "TP4", filters.pricePrecision);
                }
            } else {
                // Fallback: Place all TP orders with full quantity (if no config)
                log.warn("⚠️ No user config found - placing TPs with full quantity");
                if (trade.getTp1() != null && trade.getTp1() > 0) {
                    placeTakeProfit(symbol, side, executedQty, trade.getTp1(), "TP1", filters.pricePrecision);
                }
                if (trade.getTp2() != null && trade.getTp2() > 0) {
                    placeTakeProfit(symbol, side, executedQty, trade.getTp2(), "TP2", filters.pricePrecision);
                }
                if (trade.getTp3() != null && trade.getTp3() > 0) {
                    placeTakeProfit(symbol, side, executedQty, trade.getTp3(), "TP3", filters.pricePrecision);
                }
                if (trade.getTp4() != null && trade.getTp4() > 0) {
                    placeTakeProfit(symbol, side, executedQty, trade.getTp4(), "TP4", filters.pricePrecision);
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
        double lotSize;           // Step size for quantity
        double minNotional;       // Minimum order value
        int quantityPrecision;    // Decimal places for quantity
        double tickSize;          // Step size for price (PRICE_FILTER)
        int pricePrecision;       // Decimal places for price

        SymbolFilters(double lotSize, double minNotional, int quantityPrecision, double tickSize, int pricePrecision) {
            this.lotSize = lotSize;
            this.minNotional = minNotional;
            this.quantityPrecision = quantityPrecision;
            this.tickSize = tickSize;
            this.pricePrecision = pricePrecision;
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
                    double tickSize = 0.01;
                    int quantityPrecision = 2;
                    int pricePrecision = 2;

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

                        // Extract PRICE_FILTER
                        if ("PRICE_FILTER".equals(filterType)) {
                            tickSize = filter.getDouble("tickSize");
                            pricePrecision = getDecimalPlaces(filter.getString("tickSize"));
                            log.info("💲 PRICE_FILTER: tickSize={}, pricePrecision={}", tickSize, pricePrecision);
                        }
                    }

                    log.info("✅ Symbol filters for {}: lotSize={}, minNotional={}, qtyPrecision={}, tickSize={}, pricePrecision={}",
                        symbol, lotSize, minNotional, quantityPrecision, tickSize, pricePrecision);
                    return new SymbolFilters(lotSize, minNotional, quantityPrecision, tickSize, pricePrecision);
                }
            }

            log.warn("⚠️ Symbol {} not found in exchange info, using defaults", symbol);
            return new SymbolFilters(1.0, 10.0, 2, 0.01, 2);

        } catch (Exception e) {
            log.warn("⚠️ Error fetching exchange info for {}: {}", symbol, e.getMessage());
            return new SymbolFilters(1.0, 10.0, 2, 0.01, 2); // Default filters
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
    /**
     * ✅ DYNAMIC PRICE ROUNDING (Binance Compliant)
     * Rounds price to the symbol's pricePrecision (tickSize)
     * Examples:
     *   - pricePrecision=2 (tickSize=0.01): 0.253 → 0.25
     *   - pricePrecision=3 (tickSize=0.001): 0.253 → 0.253
     *   - pricePrecision=4 (tickSize=0.0001): 0.253456 → 0.2535
     */
    private double roundPrice(double price, int pricePrecision) {
        if (pricePrecision < 0) pricePrecision = 2;  // Default fallback
        double multiplier = Math.pow(10, pricePrecision);
        double rounded = Math.round(price * multiplier) / multiplier;
        log.debug("💲 Price rounding: {} → {} (precision: {})", price, rounded, pricePrecision);
        return rounded;
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
            params.put("marginType", marginMode); // ✅ BINANCE FAPI: Must be "marginType" (capital T - case-sensitive per official docs)
            params.put("recvWindow", 60000);

            log.info("🔧 Setting margin mode: symbol={}, marginType={}", symbol, marginMode);
            futuresClient.account().changeMarginType(params);
            log.info("✅ Margin mode successfully set to {} for {}", marginMode, symbol);
        } catch (Exception e) {
            // Note: This may fail if margin type is already set to the requested value
            log.warn("⚠️ Error setting margin mode (margintype={}): {}", marginMode, e.getMessage());
        }
    }

    /**
     * Place Stop-Loss order
     * ✅ UPDATED: Added reduce_only=true to allow orders below MIN_NOTIONAL
     * Binance allows orders < $5 notional if reduce_only=true (closing positions)
     */
    private boolean placeStopLoss(String symbol, String side, double qty, double stopPrice, int pricePrecision) {
        try {
            // ✅ ROUND STOP PRICE TO SYMBOL'S PRECISION
            double roundedStopPrice = roundPrice(stopPrice, pricePrecision);

            String slSide = side.equals("BUY") ? "SELL" : "BUY";
            LinkedHashMap<String, Object> slParams = new LinkedHashMap<>();
            slParams.put("symbol", symbol);
            slParams.put("side", slSide);
            slParams.put("type", "STOP_MARKET");
            slParams.put("quantity", qty);
            slParams.put("stopPrice", roundedStopPrice);  // ✅ ROUNDED TO PRECISION
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
    private void placeTakeProfit(String symbol, String side, double qty, double tpPrice, String label, int pricePrecision) {
        try {
            // ✅ ROUND TP PRICE TO SYMBOL'S PRECISION
            double roundedTpPrice = roundPrice(tpPrice, pricePrecision);

            // ✅ MIN_NOTIONAL VALIDATION: Check if order meets Binance minimum notional
            double tpNotional = qty * roundedTpPrice;
            if (tpNotional < 5.0) {
                log.warn("⚠️ {} order notional (${}) is below Binance minimum ($5). Will use reduce_only=true", label, tpNotional);
            }

            String tpSide = side.equals("BUY") ? "SELL" : "BUY";
            LinkedHashMap<String, Object> tpParams = new LinkedHashMap<>();
            tpParams.put("symbol", symbol);
            tpParams.put("side", tpSide);
            tpParams.put("type", "TAKE_PROFIT_MARKET");
            tpParams.put("quantity", qty);
            tpParams.put("stopPrice", roundedTpPrice);  // ✅ ROUNDED TO PRECISION
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

    // ============ TRADE HISTORY & FILTERING ============

    /**
     * ✅ NEW: Get closed trades with filtering and statistics
     *
     * Supports:
     * - Filter by symbol, date range, P&L amount, P&L %
     * - Pagination with configurable page size
     * - Sorting by various fields
     * - Summary statistics: win rate, best/worst trades, etc.
     *
     * @param filter Trade filter criteria
     * @return TradeHistoryResponseDTO with trades and statistics
     */
    public TradeHistoryResponseDTO getClosedTrades(TradeFilterDTO filter) {
        try {
            log.info("📊 Fetching closed trades with filters: symbol={}, fromDate={}, toDate={}",
                filter.getSymbol(), filter.getFromDate(), filter.getToDate());

            // Fetch all closed trades from database
            List<Trade> allClosedTrades = tradeRepository.findByStatus("CLOSED");

            // Apply filters
            List<Trade> filteredTrades = allClosedTrades.stream()
                .filter(trade -> applyTradeFilters(trade, filter))
                .sorted((t1, t2) -> compareTrades(t1, t2, filter))
                .collect(Collectors.toList());

            // Calculate pagination
            int page = filter.getPage() != null ? filter.getPage() : 0;
            int pageSize = filter.getPageSize() != null ? filter.getPageSize() : 20;
            int totalCount = filteredTrades.size();
            int totalPages = (totalCount + pageSize - 1) / pageSize;

            // Get paginated trades
            int fromIndex = page * pageSize;
            int toIndex = Math.min(fromIndex + pageSize, filteredTrades.size());
            List<Trade> paginatedTrades = fromIndex < filteredTrades.size()
                ? filteredTrades.subList(fromIndex, toIndex)
                : new ArrayList<>();

            // Calculate statistics
            return calculateTradeStatistics(paginatedTrades, filteredTrades, page, pageSize, totalCount, totalPages);

        } catch (Exception e) {
            log.error("❌ Error fetching closed trades: {}", e.getMessage());
            return TradeHistoryResponseDTO.builder()
                .trades(new ArrayList<>())
                .totalTrades(0)
                .winningTrades(0)
                .losingTrades(0)
                .build();
        }
    }

    /**
     * Apply all filters to a trade
     */
    private boolean applyTradeFilters(Trade trade, TradeFilterDTO filter) {
        // Symbol filter
        if (filter.getSymbol() != null && !filter.getSymbol().isEmpty()) {
            if (!trade.getPair().contains(filter.getSymbol())) {
                return false;
            }
        }

        // Date range filter
        if (filter.getFromDate() != null || filter.getToDate() != null) {
            OffsetDateTime tradeDate = trade.getClosedAt();
            if (tradeDate == null) return false;
            LocalDate closedLocalDate = tradeDate.atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();

            if (filter.getFromDate() != null && closedLocalDate.isBefore(filter.getFromDate())) {
                return false;
            }
            if (filter.getToDate() != null && closedLocalDate.isAfter(filter.getToDate())) {
                return false;
            }
        }

        // P&L amount filter
        if (filter.getPnlMin() != null) {
            BigDecimal tradePnl = new BigDecimal(trade.getPnl().toString());
            if (tradePnl.compareTo(filter.getPnlMin()) < 0) {
                return false;
            }
        }
        if (filter.getPnlMax() != null) {
            BigDecimal tradePnl = new BigDecimal(trade.getPnl().toString());
            if (tradePnl.compareTo(filter.getPnlMax()) > 0) {
                return false;
            }
        }

        // P&L percentage filter
        if (filter.getPnlPercentMin() != null) {
            BigDecimal tradePnlPercent = new BigDecimal(trade.getPnlPercent().toString());
            if (tradePnlPercent.compareTo(filter.getPnlPercentMin()) < 0) {
                return false;
            }
        }
        if (filter.getPnlPercentMax() != null) {
            BigDecimal tradePnlPercent = new BigDecimal(trade.getPnlPercent().toString());
            if (tradePnlPercent.compareTo(filter.getPnlPercentMax()) > 0) {
                return false;
            }
        }

        // Side filter
        if (filter.getSide() != null && !filter.getSide().isEmpty()) {
            if (!trade.getSide().equals(filter.getSide())) {
                return false;
            }
        }

        // Exit reason filter
        if (filter.getExitReason() != null && !filter.getExitReason().isEmpty()) {
            if (!filter.getExitReason().equals(trade.getExitReason())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compare trades for sorting
     */
    private int compareTrades(Trade t1, Trade t2, TradeFilterDTO filter) {
        String sortBy = filter.getSortBy() != null ? filter.getSortBy() : "closedAt";
        String sortOrder = filter.getSortOrder() != null ? filter.getSortOrder() : "DESC";

        int comparison = 0;
        switch (sortBy) {
            case "pnl":
                comparison = t1.getPnl().compareTo(t2.getPnl());
                break;
            case "pnlPercent":
                comparison = t1.getPnlPercent().compareTo(t2.getPnlPercent());
                break;
            case "symbol":
                comparison = t1.getPair().compareTo(t2.getPair());
                break;
            case "closedAt":
            default:
                comparison = t1.getClosedAt().compareTo(t2.getClosedAt());
        }

        return "ASC".equals(sortOrder) ? comparison : -comparison;
    }

    /**
     * Calculate comprehensive trade statistics
     */
    private TradeHistoryResponseDTO calculateTradeStatistics(
            List<Trade> paginatedTrades,
            List<Trade> allFilteredTrades,
            int page,
            int pageSize,
            int totalCount,
            int totalPages) {

        int totalTrades = allFilteredTrades.size();
        BigDecimal totalPnL = BigDecimal.ZERO;
        BigDecimal totalPnLPercent = BigDecimal.ZERO;
        int winningTrades = 0;
        int losingTrades = 0;
        Trade bestTrade = null;
        Trade worstTrade = null;
        BigDecimal largestWin = BigDecimal.ZERO;
        BigDecimal largestLoss = BigDecimal.ZERO;

        for (Trade trade : allFilteredTrades) {
            BigDecimal pnl = new BigDecimal(trade.getPnl().toString());
            totalPnL = totalPnL.add(pnl);
            totalPnLPercent = totalPnLPercent.add(new BigDecimal(trade.getPnlPercent().toString()));

            if (pnl.compareTo(BigDecimal.ZERO) > 0) {
                winningTrades++;
                if (largestWin.compareTo(pnl) < 0) {
                    largestWin = pnl;
                    bestTrade = trade;
                }
            } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
                losingTrades++;
                if (largestLoss.compareTo(pnl) > 0) {
                    largestLoss = pnl;
                    worstTrade = trade;
                }
            }
        }

        // Calculate averages
        BigDecimal averagePnL = totalTrades > 0
            ? totalPnL.divide(BigDecimal.valueOf(totalTrades), 8, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        BigDecimal averagePnLPct = totalTrades > 0
            ? totalPnLPercent.divide(BigDecimal.valueOf(totalTrades), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Calculate win rate
        BigDecimal winRate = totalTrades > 0
            ? BigDecimal.valueOf(winningTrades).divide(BigDecimal.valueOf(totalTrades), 4, RoundingMode.HALF_UP)
              .multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        // Calculate profit factor
        BigDecimal profitFactor = losingTrades > 0 && largestLoss.compareTo(BigDecimal.ZERO) != 0
            ? largestWin.divide(largestLoss.abs(), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        // Calculate win/loss ratio
        BigDecimal winLossRatio = losingTrades > 0
            ? BigDecimal.valueOf(winningTrades).divide(BigDecimal.valueOf(losingTrades), 2, RoundingMode.HALF_UP)
            : BigDecimal.valueOf(winningTrades);

        log.info("📈 Trade stats: Total={}, Wins={}, Losses={}, WinRate={}%, AvgP&L={}",
            totalTrades, winningTrades, losingTrades, winRate, averagePnL);

        return TradeHistoryResponseDTO.builder()
            .trades(paginatedTrades)
            .page(page)
            .pageSize(pageSize)
            .totalCount((long) totalCount)
            .totalPages(totalPages)
            .totalTrades(totalTrades)
            .winningTrades(winningTrades)
            .losingTrades(losingTrades)
            .winRate(winRate)
            .totalPnL(totalPnL)
            .averagePnL(averagePnL)
            .averagePnLPct(averagePnLPct)
            .bestTrade(bestTrade)
            .worstTrade(worstTrade)
            .largestWin(largestWin)
            .largestLoss(largestLoss)
            .profitFactor(profitFactor)
            .winLossRatio(winLossRatio)
            .build();
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
            if (futuresClient == null) {
                log.warn("⚠️ Binance client not initialized");
                return positions;
            }

            log.info("📊 Fetching open positions from Binance...");

            // Fetch position risk data from Binance
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("timestamp", System.currentTimeMillis());
            params.put("recvWindow", 60000);

            String positionRiskResponse = futuresClient.account().positionInformation(params);
            JSONArray positionsArray = new JSONArray(positionRiskResponse);

            // Fetch all open orders
            String openOrdersResponse = futuresClient.account().currentAllOpenOrders(params);
            JSONArray ordersArray = new JSONArray(openOrdersResponse);

            // Parse positions
            for (int i = 0; i < positionsArray.length(); i++) {
                JSONObject posObj = positionsArray.getJSONObject(i);

                String symbol = posObj.getString("symbol");
                String side = posObj.getString("positionSide");
                BigDecimal quantity = new BigDecimal(posObj.getString("positionAmt"));

                // Skip zero positions
                if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }

                // Get mark price for P&L calculation
                BigDecimal markPrice = getMarkPrice(symbol);
                BigDecimal entryPrice = new BigDecimal(posObj.getString("entryPrice"));
                BigDecimal unrealizedPnL = new BigDecimal(posObj.getString("unRealizedProfit"));

                // Get related orders for this symbol
                List<BinanceOrderDTO> symbolOrders = getOpenOrdersForSymbol(ordersArray, symbol);

                BinancePositionDTO position = BinancePositionDTO.builder()
                    .symbol(symbol)
                    .side(side)
                    .quantity(quantity)
                    .entryPrice(entryPrice)
                    .markPrice(markPrice)
                    .unrealizedPnL(unrealizedPnL)
                    .openOrders(symbolOrders)
                    .build();

                positions.add(position);

                log.info("✅ Position loaded: {} {} @ {} mark price: {}",
                         side, symbol, entryPrice, markPrice);
            }

            log.info("✅ Fetched {} open positions from Binance", positions.size());
            return positions;

        } catch (Exception e) {
            log.error("❌ Error fetching open positions from Binance: {}", e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Get mark price (current price) for a symbol from Binance
     * Uses 24h ticker endpoint to get the latest price
     */
    private BigDecimal getMarkPrice(String symbol) {
        try {
            if (futuresClient == null) {
                return BigDecimal.ZERO;
            }

            // Fetch 24h ticker price
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);

            String tickerResponse = futuresClient.market().ticker24H(params);
            JSONObject tickerObj = new JSONObject(tickerResponse);

            String lastPrice = tickerObj.optString("lastPrice", "0");
            BigDecimal markPrice = new BigDecimal(lastPrice);

            log.debug("📊 Mark price for {}: {}", symbol, markPrice);
            return markPrice;

        } catch (Exception e) {
            log.warn("⚠️ Could not fetch mark price for {}: {}", symbol, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get all open orders for a specific symbol from a list of all orders
     * Filters orders by symbol and builds BinanceOrderDTO objects
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
