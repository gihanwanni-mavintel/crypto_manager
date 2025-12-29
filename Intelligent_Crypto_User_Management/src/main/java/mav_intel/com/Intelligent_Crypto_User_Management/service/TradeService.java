package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeRequest;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.ExecuteTradeResponse;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeManagementConfigRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Slf4j
@Service
public class TradeService {

    // Default leverage for all trades
    private static final int DEFAULT_LEVERAGE = 20;

    // Binance minimum notional value with 2% safety buffer
    private static final double MIN_NOTIONAL_VALUE = 5.10;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private SignalRepository signalRepository;

    @Autowired
    private TradeManagementConfigRepository configRepository;

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    @org.springframework.beans.factory.annotation.Value("${binance.api.key:}")
    private String binanceApiKey;

    @org.springframework.beans.factory.annotation.Value("${binance.api.secret:}")
    private String binanceApiSecret;

    private static final String BINANCE_FUTURES_BASE_URL = "https://fapi.binance.com";

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

            // Load trade management config (default userId = 1)
            TradeManagementConfig config = getTradeConfig(1L);
            int leverage = config.getMaxLeverage().intValue();
            String marginMode = config.getMarginMode();

            log.info("📋 Using config: Leverage={}x, MarginMode={}", leverage, marginMode);

            // 1. Create Trade record in database
            Trade trade = new Trade();
            trade.setPair(request.getPair());
            trade.setSide(request.getSide());
            trade.setEntryPrice(request.getEntry());
            trade.setEntryQuantity(request.getQuantity() != null ? request.getQuantity() : 0.0);
            trade.setLeverage(leverage); // Use leverage from config
            trade.setTakeProfit(request.getTakeProfit());
            trade.setStopLoss(request.getStopLoss());
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

            // Normalize symbol: Remove .P suffix (e.g., ALICEUSDT.P -> ALICEUSDT)
            String symbol = normalizeBinanceSymbol(trade.getPair());
            String side = trade.getSide().equalsIgnoreCase("LONG") ? "BUY" : "SELL";

            // Get symbol precision from Binance
            int pricePrecision = getSymbolPricePrecision(symbol);
            int quantityPrecision = getSymbolQuantityPrecision(symbol);

            // Round prices to correct precision
            double roundedEntryPrice = roundToPrecision(trade.getEntryPrice(), pricePrecision);

            // Calculate and round quantity with minimum notional validation
            double roundedQuantity = calculateQuantity(
                trade.getEntryQuantity(),
                trade.getLeverage(),
                balance,
                roundedEntryPrice,
                quantityPrecision
            );

            // Get trade config for margin mode
            TradeManagementConfig config = getTradeConfig(1L);

            // 1. Set margin mode (ISOLATED or CROSS)
            setMarginMode(symbol, config.getMarginMode());

            // 2. Set leverage
            setLeverage(symbol, trade.getLeverage());

            // 3. Place LIMIT order at entry price
            LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
            orderParams.put("symbol", symbol);
            orderParams.put("side", side);
            orderParams.put("type", "LIMIT");
            orderParams.put("timeInForce", "GTC"); // Good Till Cancel
            orderParams.put("quantity", roundedQuantity);
            orderParams.put("price", roundedEntryPrice);
            orderParams.put("recvWindow", 60000);

            log.info("📍 Placing LIMIT order: {} {} @ ${} Qty={} (Price Precision: {}, Qty Precision: {})",
                side, symbol, roundedEntryPrice, roundedQuantity, pricePrecision, quantityPrecision);

            String orderResponse = futuresClient.account().newOrder(orderParams);
            JSONObject resp = new JSONObject(orderResponse);
            String orderId = resp.optString("orderId", "");
            String status = resp.optString("status", "FAILED");
            double executedQty = resp.optDouble("executedQty", 0.0);

            log.info("✅ LIMIT Order placed: {} {} Qty={} Status={}", side, symbol, executedQty, status);

            trade.setBinanceOrderId(orderId);

            // 4. Place Stop-Loss (using Algo API)
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                double roundedSlPrice = roundToPrecision(trade.getStopLoss(), pricePrecision);
                placeStopLoss(symbol, side, roundedQuantity, roundedSlPrice);
            }

            // 5. Place Take-Profit (using Algo API)
            if (trade.getTakeProfit() != null && trade.getTakeProfit() > 0) {
                double roundedTpPrice = roundToPrecision(trade.getTakeProfit(), pricePrecision);
                placeTakeProfit(symbol, side, roundedQuantity, roundedTpPrice);
            }

            log.info("✅ All orders placed for {} (Entry, TP, SL)", symbol);
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
     * Calculate quantity based on available balance, leverage, and minimum notional value
     * Ensures the order meets Binance's $5 minimum notional requirement
     *
     * @param requestedQty User-requested quantity (if provided)
     * @param leverage Trading leverage
     * @param balance Available USDT balance
     * @param price Entry price
     * @param precision Quantity precision from Binance
     * @return Quantity rounded to correct precision with minimum notional validation
     */
    private double calculateQuantity(Double requestedQty, int leverage, double balance, double price, int precision) {
        double quantity;

        if (requestedQty != null && requestedQty > 0) {
            quantity = requestedQty;
        } else {
            // Default: Use 50% of available balance divided by leverage
            quantity = (balance * 0.5) / leverage;
        }

        // Calculate minimum quantity needed to meet Binance's $5 notional requirement
        // Use $5.10 (2% buffer) to account for rounding
        double minQuantityForNotional = MIN_NOTIONAL_VALUE / price;

        // Use the larger of: calculated quantity OR minimum notional quantity
        quantity = Math.max(quantity, minQuantityForNotional);

        // Round to symbol-specific precision
        quantity = roundToPrecision(quantity, precision);

        // Verify that notional value is still >= $5 after rounding
        double notionalValue = quantity * price;
        if (notionalValue < 5.0) {
            // Add one precision step to ensure we meet minimum
            quantity += Math.pow(10, -precision);
            notionalValue = quantity * price;
            log.warn("⚠️ Adjusted quantity to meet min notional: {} (notional: ${})", quantity, notionalValue);
        }

        log.info("📊 Calculated quantity: {} | Notional: ${} | Min required: $5.00", quantity,
            String.format("%.2f", notionalValue));
        return quantity;
    }

    /**
     * Round to specified decimal places
     */
    private double roundToPrecision(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        return Math.round(value * scale) / scale;
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
     * Place Stop-Loss order using Binance Algo Order API
     * Required since 2025-12-09 migration to Algo Service
     * Endpoint: POST /fapi/v1/algoOrder
     */
    private boolean placeStopLoss(String symbol, String side, double qty, double stopPrice) {
        try {
            String slSide = side.equals("BUY") ? "SELL" : "BUY";

            LinkedHashMap<String, Object> algoParams = new LinkedHashMap<>();
            algoParams.put("algoType", "CONDITIONAL");
            algoParams.put("symbol", symbol);
            algoParams.put("side", slSide);
            algoParams.put("type", "STOP_MARKET");
            algoParams.put("quantity", qty);
            algoParams.put("triggerPrice", stopPrice);     // Trigger price for algo order
            algoParams.put("workingType", "MARK_PRICE");   // Use MARK_PRICE for trigger
            algoParams.put("reduceOnly", "true");          // Only close position, don't open new
            algoParams.put("recvWindow", 60000);

            // Make direct HTTP request to Algo Order API endpoint
            String resp = placeAlgoOrder(algoParams);
            log.info("🛑 Stop-Loss algo order placed: {}", resp);
            return true;
        } catch (Exception e) {
            log.error("❌ Error placing Stop-Loss: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Place Take-Profit order using Binance Algo Order API
     * Required since 2025-12-09 migration to Algo Service
     * Endpoint: POST /fapi/v1/algoOrder
     */
    private void placeTakeProfit(String symbol, String side, double qty, double tpPrice) {
        try {
            String tpSide = side.equals("BUY") ? "SELL" : "BUY";

            LinkedHashMap<String, Object> algoParams = new LinkedHashMap<>();
            algoParams.put("algoType", "CONDITIONAL");
            algoParams.put("symbol", symbol);
            algoParams.put("side", tpSide);
            algoParams.put("type", "TAKE_PROFIT_MARKET");
            algoParams.put("quantity", qty);
            algoParams.put("triggerPrice", tpPrice);       // Trigger price for algo order
            algoParams.put("workingType", "MARK_PRICE");   // Use MARK_PRICE for trigger
            algoParams.put("reduceOnly", "true");          // Only close position, don't open new
            algoParams.put("recvWindow", 60000);

            // Make direct HTTP request to Algo Order API endpoint
            String resp = placeAlgoOrder(algoParams);
            log.info("📈 Take-Profit algo order placed: {}", resp);
        } catch (Exception e) {
            log.error("❌ Error placing Take-Profit: {}", e.getMessage(), e);
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
            // Normalize symbol: Remove .P suffix
            String symbol = normalizeBinanceSymbol(trade.getPair());
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
     * Get ALL real-time active positions from Binance account
     * This fetches live position data directly from Binance, including:
     * - Positions opened via this app
     * - Positions opened manually via Binance web/mobile
     * - Real-time P&L and current prices
     *
     * Endpoint: GET /fapi/v2/positionRisk
     */
    public List<Trade> getRealTimePositionsFromBinance() {
        List<Trade> positions = new java.util.ArrayList<>();

        if (futuresClient == null) {
            log.warn("⚠️ Binance client not available. Cannot fetch real-time positions.");
            return positions;
        }

        try {
            log.info("📡 Fetching real-time positions from Binance account...");

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000);

            String response = futuresClient.account().positionInformation(params);
            org.json.JSONArray positionsArray = new org.json.JSONArray(response);

            log.info("📊 Received {} position entries from Binance", positionsArray.length());

            int activePositions = 0;
            for (int i = 0; i < positionsArray.length(); i++) {
                org.json.JSONObject pos = positionsArray.getJSONObject(i);

                // Only include positions with non-zero quantity
                double positionAmt = Math.abs(pos.optDouble("positionAmt", 0.0));
                if (positionAmt == 0) {
                    continue; // Skip closed positions
                }

                activePositions++;

                // Extract position data from Binance response
                String symbol = pos.optString("symbol", "");
                double entryPrice = pos.optDouble("entryPrice", 0.0);
                double markPrice = pos.optDouble("markPrice", 0.0); // Current market price
                double unrealizedProfit = pos.optDouble("unRealizedProfit", 0.0);
                int leverage = pos.optInt("leverage", 1);
                String marginType = pos.optString("marginType", "isolated");

                // Determine if LONG or SHORT based on position amount sign
                String side = positionAmt > 0 ? "LONG" : "SHORT";

                // Calculate P&L percentage
                double pnlPercent = 0.0;
                if (entryPrice > 0 && positionAmt > 0) {
                    double notionalValue = Math.abs(positionAmt * entryPrice);
                    if (notionalValue > 0) {
                        pnlPercent = (unrealizedProfit / notionalValue) * 100;
                    }
                }

                // Create Trade object with live Binance data
                Trade trade = new Trade();
                trade.setPair(symbol);
                trade.setSide(side);
                trade.setEntryPrice(entryPrice);
                trade.setEntryQuantity(Math.abs(positionAmt));
                trade.setLeverage(leverage);
                trade.setPnl(unrealizedProfit);
                trade.setPnlPercent(pnlPercent);
                trade.setStatus("OPEN");

                // Store current mark price in a custom field (we'll use exitPrice temporarily for this)
                // In a real implementation, you might want to add a 'currentPrice' field to Trade model
                trade.setExitPrice(markPrice);

                positions.add(trade);

                log.info("✅ Active position: {} {} | Entry: ${} | Current: ${} | Qty: {} | P&L: ${} ({} %)",
                    side, symbol, entryPrice, markPrice, positionAmt, unrealizedProfit,
                    String.format("%.2f", pnlPercent));
            }

            log.info("📊 Total active positions found: {}", activePositions);

        } catch (Exception e) {
            log.error("❌ Error fetching real-time positions from Binance: {}", e.getMessage(), e);
        }

        return positions;
    }

    /**
     * Get symbol price precision from Binance Exchange Info API
     */
    private int getSymbolPricePrecision(String symbol) {
        try {
            String result = futuresClient.market().exchangeInfo();
            JSONObject response = new JSONObject(result);

            var symbols = response.getJSONArray("symbols");
            for (int i = 0; i < symbols.length(); i++) {
                JSONObject symbolInfo = symbols.getJSONObject(i);
                if (symbol.equals(symbolInfo.getString("symbol"))) {
                    int pricePrecision = symbolInfo.getInt("pricePrecision");
                    log.info("📊 Symbol {} price precision: {}", symbol, pricePrecision);
                    return pricePrecision;
                }
            }

            log.warn("⚠️ Could not find price precision for {}. Using default: 2", symbol);
            return 2; // Default fallback
        } catch (Exception e) {
            log.error("❌ Error fetching price precision: {}", e.getMessage());
            return 2; // Default fallback
        }
    }

    /**
     * Get symbol quantity precision from Binance Exchange Info API
     */
    private int getSymbolQuantityPrecision(String symbol) {
        try {
            String result = futuresClient.market().exchangeInfo();
            JSONObject response = new JSONObject(result);

            var symbols = response.getJSONArray("symbols");
            for (int i = 0; i < symbols.length(); i++) {
                JSONObject symbolInfo = symbols.getJSONObject(i);
                if (symbol.equals(symbolInfo.getString("symbol"))) {
                    int quantityPrecision = symbolInfo.getInt("quantityPrecision");
                    log.info("📊 Symbol {} quantity precision: {}", symbol, quantityPrecision);
                    return quantityPrecision;
                }
            }

            log.warn("⚠️ Could not find quantity precision for {}. Using default: 0", symbol);
            return 0; // Default fallback (whole numbers)
        } catch (Exception e) {
            log.error("❌ Error fetching quantity precision: {}", e.getMessage());
            return 0; // Default fallback
        }
    }

    /**
     * Normalize symbol for Binance API
     * Removes .P suffix (e.g., ALICEUSDT.P -> ALICEUSDT)
     */
    private String normalizeBinanceSymbol(String symbol) {
        if (symbol == null) return null;
        // Remove .P suffix if present (Telegram notation for perpetual futures)
        return symbol.replaceAll("\\.P$", "");
    }

    /**
     * Place Algo Order using Binance Algo Order API
     * Endpoint: POST /fapi/v1/algoOrder
     * Required since 2025-12-09 migration to Algo Service for conditional orders
     */
    private String placeAlgoOrder(LinkedHashMap<String, Object> params) throws Exception {
        // Add timestamp
        params.put("timestamp", System.currentTimeMillis());

        // Build query string
        String queryString = params.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));

        // Generate signature using HMAC-SHA256
        String signature = generateSignature(queryString, binanceApiSecret);
        String signedQueryString = queryString + "&signature=" + signature;

        // Build HTTP request
        String url = BINANCE_FUTURES_BASE_URL + "/fapi/v1/algoOrder?" + signedQueryString;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("X-MBX-APIKEY", binanceApiKey)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();

        log.info("📡 Algo Order API Request: POST {}", url.substring(0, Math.min(url.length(), 100)) + "...");

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Algo Order API error: " + response.body());
        }

        return response.body();
    }

    /**
     * Generate HMAC-SHA256 signature for Binance API authentication
     */
    private String generateSignature(String data, String secret) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKeySpec);
        byte[] hash = hmacSha256.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Convert to hex string
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * Get trade management config or create default
     */
    private TradeManagementConfig getTradeConfig(Long userId) {
        return configRepository.findByUserId(userId).orElseGet(() -> {
            TradeManagementConfig defaultConfig = new TradeManagementConfig();
            defaultConfig.setUserId(userId);
            return configRepository.save(defaultConfig);
        });
    }

    /**
     * Set margin mode for symbol (ISOLATED or CROSS)
     * Endpoint: POST /fapi/v1/marginType
     */
    private void setMarginMode(String symbol, String marginMode) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("marginType", marginMode.toUpperCase()); // ISOLATED or CROSSED
            params.put("recvWindow", 60000);
            futuresClient.account().changeMarginType(params);
            log.info("⚙️ Margin mode set to {} for {}", marginMode, symbol);
        } catch (Exception e) {
            // If margin mode is already set, Binance returns error -4046
            // This is OK, we can ignore it
            if (e.getMessage().contains("-4046") || e.getMessage().contains("No need to change margin type")) {
                log.debug("ℹ️ Margin mode already set to {} for {}", marginMode, symbol);
            } else {
                log.warn("⚠️ Error setting margin mode: {}", e.getMessage());
            }
        }
    }
}
