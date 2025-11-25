package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO representing an open position from Binance Futures
 * Contains position details with real-time P&L calculations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BinancePositionDTO {

    // ============ POSITION IDENTIFIER ============
    private String symbol;              // e.g., "BTCUSDT", "ETHUSD", "SOLUSDT"
    private String side;                // "LONG" or "SHORT"

    // ============ PRICE INFORMATION ============
    private BigDecimal entryPrice;      // Entry price of the position
    private BigDecimal markPrice;       // Current mark price from Binance
    private BigDecimal liquidationPrice; // Liquidation price

    // ============ POSITION SIZE ============
    private BigDecimal quantity;        // Current position quantity
    private Integer leverage;           // Current leverage (1x - 125x)

    // ============ P&L INFORMATION ============
    private BigDecimal unrealizedPnL;    // Unrealized profit/loss in USDT
    private BigDecimal unrealizedPnLPct; // Unrealized P&L percentage (e.g., 2.5)

    // ============ RELATED ORDERS ============
    private List<BinanceOrderDTO> openOrders; // Entry, TP, SL orders for this position

    // ============ POSITION STATUS ============
    private String marginType;         // "isolated" or "cross"
    private Long openedAt;              // Timestamp when position was opened (milliseconds)
    private Boolean isReduceOnly;       // Is position in reduce-only mode
}
