package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * DTO representing an open order from Binance Futures
 * Includes LIMIT entry orders, STOP_MARKET stop-loss, and TAKE_PROFIT_MARKET orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinanceOrderDTO {

    // ============ ORDER IDENTIFIER ============
    private String orderId;             // Binance order ID
    private String clientOrderId;       // Client order ID

    // ============ ORDER TYPE & DIRECTION ============
    private String symbol;              // Trading pair (e.g., "BTCUSDT")
    private String side;                // "BUY" or "SELL"
    private String type;                // "LIMIT", "MARKET", "STOP_MARKET", "TAKE_PROFIT_MARKET"

    // ============ ORDER PRICE & QUANTITY ============
    private BigDecimal price;           // Limit price (0 for market orders)
    private BigDecimal stopPrice;       // Stop price for SL and TP orders
    private BigDecimal quantity;        // Order quantity
    private BigDecimal executedQuantity; // Already executed quantity
    private BigDecimal cumulativeQuoteAsset; // Cumulative quote asset spent

    // ============ ORDER STATUS ============
    private String status;              // "NEW", "PARTIALLY_FILLED", "FILLED", "CANCELED", etc.
    private String timeInForce;         // "GTC", "IOC", "FOK"
    private Boolean reduceOnly;         // Whether order is reduce-only
    private Boolean postOnly;           // Post-only order

    // ============ ORDER CLASSIFICATION ============
    private String orderLabel;          // "ENTRY", "TP1", "TP2", "TP3", "TP4", "SL" (for display)

    // ============ TIMESTAMPS ============
    private Long createTime;            // Order creation time (milliseconds)
    private Long updateTime;            // Last update time (milliseconds)
}
