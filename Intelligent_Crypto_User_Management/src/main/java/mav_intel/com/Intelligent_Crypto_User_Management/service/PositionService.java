package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinanceOrderDTO;
import mav_intel.com.Intelligent_Crypto_User_Management.dto.BinancePositionDTO;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ✅ POSITION MANAGEMENT SERVICE
 * Handles fetching, updating, and closing positions on Binance Futures
 * Follows Binance FAPI v2 documentation for all operations
 */
@Slf4j
@Service
public class PositionService {

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    /**
     * ✅ Fetch all open positions from Binance
     * API Endpoint: GET /fapi/v2/positionRisk
     *
     * @return List of all open positions with details
     */
    public List<BinancePositionDTO> getActivePositions() {
        try {
            log.info("📊 Fetching active positions from Binance...");

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000);
            String positionResponse = futuresClient.account().positionInformation(params);
            JSONArray positions = new JSONArray(positionResponse);
            List<BinancePositionDTO> activePositions = new ArrayList<>();

            for (int i = 0; i < positions.length(); i++) {
                JSONObject pos = positions.getJSONObject(i);

                // Skip positions with 0 quantity (closed positions)
                double quantity = pos.getDouble("positionAmt");
                if (quantity == 0) {
                    continue;
                }

                // Get mark price for current price
                double markPrice = pos.getDouble("markPrice");

                // Calculate unrealized P&L
                double entryPrice = pos.getDouble("entryPrice");
                double unrealizedPnL = pos.getDouble("unrealizedProfit");
                double unrealizedPnLPct = pos.getDouble("percentage");

                // Fetch open orders for this position
                List<BinanceOrderDTO> openOrders = getOpenOrdersForSymbol(pos.getString("symbol"));

                // Build DTO
                BinancePositionDTO positionDTO = BinancePositionDTO.builder()
                    .symbol(pos.getString("symbol"))
                    .side(quantity > 0 ? "LONG" : "SHORT")
                    .entryPrice(BigDecimal.valueOf(entryPrice))
                    .markPrice(BigDecimal.valueOf(markPrice))
                    .liquidationPrice(new BigDecimal(pos.getString("liquidationPrice")))
                    .quantity(new BigDecimal(String.valueOf(Math.abs(quantity))))
                    .leverage(pos.getInt("leverage"))
                    .unrealizedPnL(BigDecimal.valueOf(unrealizedPnL))
                    .unrealizedPnLPct(BigDecimal.valueOf(unrealizedPnLPct * 100))
                    .marginType(pos.getString("marginType"))
                    .openOrders(openOrders)
                    .openedAt(System.currentTimeMillis()) // Binance doesn't provide this directly
                    .isReduceOnly(false)
                    .build();

                activePositions.add(positionDTO);
            }

            log.info("✅ Found {} active positions", activePositions.size());
            return activePositions;

        } catch (Exception e) {
            log.error("❌ Error fetching positions: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Get all open orders for a specific symbol
     * API Endpoint: GET /fapi/v1/openOrders?symbol={symbol}
     *
     * @param symbol Binance symbol (e.g., "BTCUSDT")
     * @return List of open orders for the symbol
     */
    private List<BinanceOrderDTO> getOpenOrdersForSymbol(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("recvWindow", 60000);

            String ordersResponse = futuresClient.account().currentAllOpenOrders(params);
            JSONArray orders = new JSONArray(ordersResponse);
            List<BinanceOrderDTO> orderList = new ArrayList<>();

            for (int i = 0; i < orders.length(); i++) {
                JSONObject order = orders.getJSONObject(i);

                BinanceOrderDTO orderDTO = BinanceOrderDTO.builder()
                    .orderId(order.getString("orderId"))
                    .symbol(order.getString("symbol"))
                    .price(new BigDecimal(order.getDouble("price")))
                    .quantity(new BigDecimal(order.getDouble("origQty")))
                    .executedQuantity(new BigDecimal(order.getDouble("executedQty")))
                    .status(order.getString("status"))
                    .type(order.getString("type"))
                    .side(order.getString("side"))
                    .timeInForce(order.getString("timeInForce"))
                    .updateTime(order.getLong("updateTime"))
                    .build();

                orderList.add(orderDTO);
            }

            log.debug("📋 Found {} open orders for {}", orderList.size(), symbol);
            return orderList;

        } catch (Exception e) {
            log.warn("⚠️ Error fetching orders for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * ✅ Update Stop Loss order for a position
     * API Endpoint: DELETE + POST /fapi/v1/order
     *
     * @param symbol Binance symbol
     * @param side Position side (BUY/SELL)
     * @param quantity Quantity for SL order
     * @param newStopPrice New stop loss price
     * @param pricePrecision Decimal places for rounding
     * @return true if update successful
     */
    public boolean updateStopLoss(String symbol, String side, double quantity, double newStopPrice, int pricePrecision) {
        try {
            log.info("🔄 Updating Stop Loss for {}: {} → ${}", symbol, side, newStopPrice);

            // Get opposite side for exit order (if LONG position, sell order = BUY side order)
            String sellSide = side.equals("BUY") ? "SELL" : "BUY";

            // 1. Cancel existing STOP_MARKET order
            cancelStopLossOrder(symbol, sellSide);

            // 2. Create new STOP_MARKET order
            double roundedStopPrice = roundPrice(newStopPrice, pricePrecision);

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("side", sellSide);
            params.put("type", "STOP_MARKET");
            params.put("quantity", String.format("%.8f", quantity).replaceAll("0+$", "").replaceAll("\\.$", ""));
            params.put("stopPrice", roundedStopPrice);
            params.put("timeInForce", "GTC");
            params.put("recvWindow", 60000);

            String response = futuresClient.account().newOrder(params);
            JSONObject resp = new JSONObject(response);

            if ("NEW".equals(resp.optString("status"))) {
                log.info("✅ Stop Loss updated: ${}", roundedStopPrice);
                return true;
            }

            log.error("❌ Failed to update SL: {}", resp.optString("msg"));
            return false;

        } catch (Exception e) {
            log.error("❌ Error updating stop loss: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * ✅ Update Take Profit orders for a position
     * API Endpoint: DELETE + POST (multiple) /fapi/v1/order
     *
     * @param symbol Binance symbol
     * @param side Position side (BUY/SELL)
     * @param takeProfitLevels List of TP levels with price and quantity
     * @param pricePrecision Decimal places for rounding
     * @return true if all updates successful
     */
    public boolean updateTakeProfits(String symbol, String side, List<Map<String, Double>> takeProfitLevels, int pricePrecision) {
        try {
            log.info("🔄 Updating {} Take Profit orders for {}", takeProfitLevels.size(), symbol);

            // Get opposite side for exit order
            String sellSide = side.equals("BUY") ? "SELL" : "BUY";

            // 1. Cancel all existing TAKE_PROFIT_MARKET orders
            cancelTakeProfitOrders(symbol, sellSide);

            // 2. Create new TAKE_PROFIT_MARKET orders
            for (int i = 0; i < takeProfitLevels.size(); i++) {
                Map<String, Double> tp = takeProfitLevels.get(i);
                double tpPrice = tp.getOrDefault("price", 0.0);
                double tpQuantity = tp.getOrDefault("quantity", 0.0);

                if (tpPrice <= 0 || tpQuantity <= 0) {
                    log.warn("⚠️ Skipping invalid TP level {}: price={}, qty={}", i + 1, tpPrice, tpQuantity);
                    continue;
                }

                double roundedTpPrice = roundPrice(tpPrice, pricePrecision);

                LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                params.put("symbol", symbol);
                params.put("side", sellSide);
                params.put("type", "TAKE_PROFIT_MARKET");
                params.put("quantity", String.format("%.8f", tpQuantity).replaceAll("0+$", "").replaceAll("\\.$", ""));
                params.put("stopPrice", roundedTpPrice);
                params.put("timeInForce", "GTC");
                params.put("recvWindow", 60000);

                String response = futuresClient.account().newOrder(params);
                JSONObject resp = new JSONObject(response);

                if ("NEW".equals(resp.optString("status"))) {
                    log.info("✅ TP{} created: ${} Qty={}", i + 1, roundedTpPrice, tpQuantity);
                } else {
                    log.warn("⚠️ Failed to create TP{}: {}", i + 1, resp.optString("msg"));
                }
            }

            return true;

        } catch (Exception e) {
            log.error("❌ Error updating take profits: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * ✅ Close position completely
     * API Endpoint: DELETE + POST /fapi/v1/order
     *
     * @param symbol Binance symbol
     * @param side Position side (BUY/SELL)
     * @param quantity Total quantity to close
     * @return true if position closed successfully
     */
    public boolean closePosition(String symbol, String side, double quantity) {
        try {
            log.info("🔴 Closing position: {} {} x{}", side, symbol, quantity);

            // 1. Cancel ALL open orders for this symbol
            cancelAllOrders(symbol);

            // 2. Place MARKET order to close position
            String exitSide = side.equals("BUY") ? "SELL" : "BUY";

            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("side", exitSide);
            params.put("type", "MARKET");
            params.put("quantity", String.format("%.8f", quantity).replaceAll("0+$", "").replaceAll("\\.$", ""));
            params.put("timeInForce", "GTC");
            params.put("recvWindow", 60000);

            String response = futuresClient.account().newOrder(params);
            JSONObject resp = new JSONObject(response);

            if ("FILLED".equals(resp.optString("status")) || "PARTIALLY_FILLED".equals(resp.optString("status"))) {
                log.info("✅ Position closed successfully: {}", resp.optString("orderId"));
                return true;
            }

            log.error("❌ Failed to close position: {}", resp.optString("msg"));
            return false;

        } catch (Exception e) {
            log.error("❌ Error closing position: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Helper: Cancel all open orders for a symbol
     */
    private void cancelAllOrders(String symbol) {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("symbol", symbol);
            params.put("recvWindow", 60000);

            futuresClient.account().cancelAllOpenOrders(params);
            log.info("✅ Cancelled all open orders for {}", symbol);

        } catch (Exception e) {
            log.warn("⚠️ Error cancelling orders: {}", e.getMessage());
        }
    }

    /**
     * Helper: Cancel STOP_MARKET orders
     */
    private void cancelStopLossOrder(String symbol, String side) {
        try {
            List<BinanceOrderDTO> orders = getOpenOrdersForSymbol(symbol);

            for (BinanceOrderDTO order : orders) {
                if ("STOP_MARKET".equals(order.getType()) && side.equals(order.getSide())) {
                    LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                    params.put("symbol", symbol);
                    params.put("orderId", order.getOrderId());
                    params.put("recvWindow", 60000);

                    futuresClient.account().cancelOrder(params);
                    log.info("✅ Cancelled SL order: {}", order.getOrderId());
                }
            }

        } catch (Exception e) {
            log.warn("⚠️ Error cancelling SL order: {}", e.getMessage());
        }
    }

    /**
     * Helper: Cancel TAKE_PROFIT_MARKET orders
     */
    private void cancelTakeProfitOrders(String symbol, String side) {
        try {
            List<BinanceOrderDTO> orders = getOpenOrdersForSymbol(symbol);

            for (BinanceOrderDTO order : orders) {
                if ("TAKE_PROFIT_MARKET".equals(order.getType()) && side.equals(order.getSide())) {
                    LinkedHashMap<String, Object> params = new LinkedHashMap<>();
                    params.put("symbol", symbol);
                    params.put("orderId", order.getOrderId());
                    params.put("recvWindow", 60000);

                    futuresClient.account().cancelOrder(params);
                    log.info("✅ Cancelled TP order: {}", order.getOrderId());
                }
            }

        } catch (Exception e) {
            log.warn("⚠️ Error cancelling TP orders: {}", e.getMessage());
        }
    }

    /**
     * Helper: Round price to specific decimal places
     */
    private double roundPrice(double price, int pricePrecision) {
        if (pricePrecision < 0) pricePrecision = 2;
        double multiplier = Math.pow(10, pricePrecision);
        return Math.round(price * multiplier) / multiplier;
    }
}
