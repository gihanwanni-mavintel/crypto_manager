package mav_intel.com.Intelligent_Crypto_User_Management.service;

import com.binance.connector.futures.client.impl.UMFuturesClientImpl;
import lombok.extern.slf4j.Slf4j;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.TradeRepository;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Service for managing Binance User Data Stream (WebSocket)
 * Listens for real-time order updates and places TP/SL when entry orders fill
 */
@Slf4j
@Service
public class BinanceUserDataStreamService {

    @Autowired(required = false)
    private UMFuturesClientImpl futuresClient;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TradeService tradeService;

    private String listenKey;
    private WebSocket webSocket;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private volatile boolean isRunning = false;

    /**
     * Start the user data stream when the application starts
     */
    @PostConstruct
    public void start() {
        if (futuresClient == null) {
            log.warn("⚠️ Binance client not available. User data stream will not start.");
            return;
        }

        try {
            log.info("🚀 Starting Binance User Data Stream...");
            createListenKey();
            connectWebSocket();
            isRunning = true;
            log.info("✅ Binance User Data Stream started successfully");
        } catch (Exception e) {
            log.error("❌ Failed to start user data stream: {}", e.getMessage(), e);
        }
    }

    /**
     * Stop the user data stream when the application shuts down
     */
    @PreDestroy
    public void stop() {
        isRunning = false;
        try {
            if (webSocket != null) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Application shutdown");
                log.info("🔌 User data stream WebSocket closed");
            }
            if (listenKey != null && futuresClient != null) {
                futuresClient.userData().closeListenKey();
                log.info("🔑 ListenKey closed");
            }
        } catch (Exception e) {
            log.error("❌ Error stopping user data stream: {}", e.getMessage());
        }
    }

    /**
     * Create a new listenKey for the user data stream
     */
    private void createListenKey() {
        try {
            String response = futuresClient.userData().createListenKey();
            JSONObject json = new JSONObject(response);
            listenKey = json.getString("listenKey");
            log.info("🔑 Created listenKey: {}...", listenKey.substring(0, 10));
        } catch (Exception e) {
            log.error("❌ Failed to create listenKey: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create listenKey", e);
        }
    }

    /**
     * Keep the listenKey alive by pinging it every 30 minutes
     * Binance requires this to keep the stream active
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // Every 30 minutes
    public void keepAliveListenKey() {
        if (!isRunning || listenKey == null || futuresClient == null) {
            return;
        }

        try {
            futuresClient.userData().extendListenKey();
            log.debug("💓 ListenKey keepalive sent");
        } catch (Exception e) {
            log.error("❌ Failed to keep listenKey alive: {}", e.getMessage());
            // Try to reconnect
            try {
                log.info("🔄 Attempting to reconnect user data stream...");
                createListenKey();
                connectWebSocket();
            } catch (Exception reconnectError) {
                log.error("❌ Failed to reconnect: {}", reconnectError.getMessage());
            }
        }
    }

    /**
     * Connect to Binance User Data Stream WebSocket
     */
    private void connectWebSocket() {
        try {
            String wsUrl = "wss://fstream.binance.com/ws/" + listenKey;
            log.info("🔌 Connecting to Binance User Data Stream: {}", wsUrl);

            webSocket = httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        log.info("✅ WebSocket connection opened");
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        handleMessage(data.toString());
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        log.warn("⚠️ WebSocket closed: {} - {}", statusCode, reason);
                        if (isRunning) {
                            reconnect();
                        }
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.error("❌ WebSocket error: {}", error.getMessage(), error);
                        if (isRunning) {
                            reconnect();
                        }
                    }
                }).join();

        } catch (Exception e) {
            log.error("❌ Failed to connect WebSocket: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to connect WebSocket", e);
        }
    }

    /**
     * Reconnect to WebSocket after disconnect
     */
    private void reconnect() {
        try {
            Thread.sleep(5000); // Wait 5 seconds before reconnecting
            log.info("🔄 Reconnecting to user data stream...");
            createListenKey();
            connectWebSocket();
        } catch (Exception e) {
            log.error("❌ Failed to reconnect: {}", e.getMessage());
        }
    }

    /**
     * Handle incoming WebSocket messages
     */
    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            String eventType = json.optString("e", "");

            // Handle ORDER_TRADE_UPDATE events
            if ("ORDER_TRADE_UPDATE".equals(eventType)) {
                handleOrderUpdate(json);
            }
        } catch (Exception e) {
            log.error("❌ Error handling message: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle order update events
     * Places TP/SL when entry order is FILLED
     */
    private void handleOrderUpdate(JSONObject json) {
        try {
            JSONObject order = json.getJSONObject("o");
            String symbol = order.getString("s");
            String orderStatus = order.getString("X");
            String clientOrderId = order.optString("c", "");
            long orderId = order.getLong("i");

            log.info("📊 Order Update: {} {} - Status: {}", symbol, orderId, orderStatus);

            // Only process FILLED orders
            if (!"FILLED".equals(orderStatus)) {
                log.debug("Order not filled yet: {}", orderStatus);
                return;
            }

            // Find the trade record for this order
            Trade trade = tradeRepository.findByBinanceOrderId(String.valueOf(orderId))
                .orElse(null);

            if (trade == null) {
                log.debug("No trade found for order ID: {}", orderId);
                return;
            }

            // Check if TP/SL already placed
            if ("OPEN".equals(trade.getStatus())) {
                log.debug("Trade {} already has TP/SL placed", trade.getId());
                return;
            }

            log.info("🎯 Entry order FILLED for trade ID: {} | Pair: {} | Entry: ${}",
                trade.getId(), trade.getPair(), trade.getEntryPrice());

            // Get actual fill price and quantity
            double avgPrice = order.getDouble("ap"); // Average fill price
            double filledQty = order.getDouble("z"); // Filled quantity

            log.info("📈 Fill details: Avg Price: ${} | Quantity: {}", avgPrice, filledQty);

            // Update trade with actual fill details
            trade.setEntryPrice(avgPrice);
            trade.setEntryQuantity(filledQty);
            trade.setStatus("PENDING"); // Still pending TP/SL placement
            tradeRepository.save(trade);

            // Now place TP and SL
            log.info("🎯 Placing TP/SL for filled order...");
            placeTPandSL(trade);

        } catch (Exception e) {
            log.error("❌ Error handling order update: {}", e.getMessage(), e);
        }
    }

    /**
     * Place Take-Profit and Stop-Loss orders after entry is filled
     */
    private void placeTPandSL(Trade trade) {
        try {
            String symbol = trade.getPair().replaceAll("\\.P$", ""); // Remove .P suffix
            String side = trade.getSide();
            double quantity = trade.getEntryQuantity();

            // Place Stop-Loss
            if (trade.getStopLoss() != null && trade.getStopLoss() > 0) {
                boolean slPlaced = tradeService.placeStopLossForTrade(
                    trade.getId(), symbol, side, quantity, trade.getStopLoss()
                );
                if (slPlaced) {
                    log.info("✅ Stop-Loss placed at ${}", trade.getStopLoss());
                }
            }

            // Place Take-Profit
            if (trade.getTakeProfit() != null && trade.getTakeProfit() > 0) {
                boolean tpPlaced = tradeService.takeProfitForTrade(
                    trade.getId(), symbol, side, quantity, trade.getTakeProfit()
                );
                if (tpPlaced) {
                    log.info("✅ Take-Profit placed at ${}", trade.getTakeProfit());
                }
            }

            // Update trade status to OPEN
            trade.setStatus("OPEN");
            tradeRepository.save(trade);

            log.info("🎉 Trade ID {} is now OPEN with TP/SL active", trade.getId());

        } catch (Exception e) {
            log.error("❌ Error placing TP/SL: {}", e.getMessage(), e);
        }
    }
}
