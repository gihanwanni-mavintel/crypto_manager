package com.example.binance_trader.Service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import com.example.binance_trader.model.Signal;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
@Slf4j
public class BinanceService {

    private final UMFuturesClientImpl futuresClient;

    public BinanceService(UMFuturesClientImpl futuresClient) {
        this.futuresClient = futuresClient;
    }

    /** ✅ Get USDT balance */
    public double getBalance() {
        try {
            LinkedHashMap<String, Object> params = new LinkedHashMap<>();
            params.put("recvWindow", 60000); // add recvWindow
            String result = futuresClient.account().futuresAccountBalance(params);
            var balances = new org.json.JSONArray(result);
            for (int i = 0; i < balances.length(); i++) {
                var balance = balances.getJSONObject(i);
                if ("USDT".equals(balance.getString("asset"))) {
                    double available = balance.getDouble("availableBalance");
                    log.info("USDT Balance: {}", available);
                    return available;
                }
            }
        } catch (Exception e) {
            log.error("Error fetching balance: ", e);
        }
        return 0.0;
    }

    /** ✅ Place a MARKET futures order with SL and TP according to signal */
    public void placeFuturesOrder(Signal signal) {
        try {
            double balance = getBalance();
            if (balance <= 10) {
                log.error("Not enough balance to trade. USDT={}", balance);
                return;
            }

            String symbol = signal.getPair().replace(".P", "");
            String side = signal.getSetupType().equalsIgnoreCase("LONG") ? "BUY" : "SELL";

            // 1️⃣ Set leverage
            LinkedHashMap<String, Object> leverageParams = new LinkedHashMap<>();
            leverageParams.put("symbol", symbol);
            leverageParams.put("leverage", signal.getLeverage());
            leverageParams.put("recvWindow", 60000);
            futuresClient.account().changeInitialLeverage(leverageParams);
            log.info("Leverage set to {}x for {}", signal.getLeverage(), symbol);

            // 2️⃣ Calculate risk-based quantity (1% risk)
            double riskAmount = balance * 0.01;
            double riskPerUnit = Math.abs(signal.getEntry() - signal.getStopLoss());

            double qty = Math.floor((riskAmount / riskPerUnit) * 1000) / 1000;
            if (qty <= 0) {
                log.error("Quantity too small to trade. Qty={}", qty);
                return;
            }

            // 3️⃣ Place MARKET order
            LinkedHashMap<String, Object> orderParams = new LinkedHashMap<>();
            orderParams.put("symbol", symbol);
            orderParams.put("side", side);
            orderParams.put("type", "MARKET");
            orderParams.put("quantity", qty);
            orderParams.put("recvWindow", 60000);

            String orderResponse = futuresClient.account().newOrder(orderParams);
            JSONObject resp = new JSONObject(orderResponse);
            double executedQty = resp.optDouble("executedQty", 0.0);
            String status = resp.getString("status");

            log.info("Order placed: {} {} Qty={} Status={}", side, symbol, qty, status);

            if ("NEW".equals(status) && executedQty == 0.0) {
                log.warn("Testnet MARKET order not executed yet. Qty={}, consider retrying or using LIMIT.", qty);
                return;
            }

            // 4️⃣ Place Stop-Loss
            if (!placeStopLoss(symbol, side, executedQty, signal.getStopLoss())) {
                log.error("SL failed, manual intervention required for {}", symbol);
                return;
            }

            // 5️⃣ Place Take-Profits
            if (signal.getTp1() > 0) placeTakeProfit(symbol, side, executedQty, signal.getTp1(), "TP1");
            if (signal.getTp2() > 0) placeTakeProfit(symbol, side, executedQty, signal.getTp2(), "TP2");
            if (signal.getTp3() > 0) placeTakeProfit(symbol, side, executedQty, signal.getTp3(), "TP3");
            if (signal.getTp4() > 0) placeTakeProfit(symbol, side, executedQty, signal.getTp4(), "TP4");

            log.info("✅ All orders placed for {}", symbol);

        } catch (Exception e) {
            log.error("Error placing order for {}: {}", signal.getPair(), e.getMessage());
        }
    }

    /** ✅ Place Stop-Loss order */
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
            log.info("Stop-Loss placed: {}", resp);
            return true;
        } catch (Exception e) {
            log.error("Error placing Stop-Loss for {}: {}", symbol, e.getMessage());
            return false;
        }
    }

    /** ✅ Place Take-Profit order */
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
            log.info("{} placed: {}", label, resp);
        } catch (Exception e) {
            log.error("Error placing {} for {}: {}", label, symbol, e.getMessage());
        }
    }
}
