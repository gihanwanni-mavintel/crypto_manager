package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for trade history response
 * Contains filtered trades and summary statistics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeHistoryResponseDTO {

    // ============ TRADES LIST ============
    private List<Trade> trades;        // Filtered list of trades

    // ============ PAGINATION INFO ============
    private Integer page;              // Current page number (0-indexed)
    private Integer pageSize;          // Trades per page
    private Long totalCount;           // Total number of trades matching filter
    private Integer totalPages;        // Total number of pages

    // ============ SUMMARY STATISTICS ============
    private Integer totalTrades;       // Total number of trades
    private Integer winningTrades;     // Number of profitable trades
    private Integer losingTrades;      // Number of losing trades
    private BigDecimal winRate;        // Win rate percentage (0-100)

    // ============ P&L STATISTICS ============
    private BigDecimal totalPnL;       // Sum of all P&L amounts
    private BigDecimal averagePnL;     // Average P&L per trade
    private BigDecimal averagePnLPct;  // Average P&L percentage

    // ============ TRADE EXTREMES ============
    private Trade bestTrade;           // Trade with highest P&L
    private Trade worstTrade;          // Trade with lowest P&L
    private BigDecimal largestWin;     // Largest profitable trade amount
    private BigDecimal largestLoss;    // Largest losing trade amount

    // ============ PERFORMANCE METRICS ============
    private BigDecimal profitFactor;   // Sum of wins / Sum of losses (if losses > 0)
    private BigDecimal winLossRatio;   // Number of wins / Number of losses (if losses > 0)
    private Integer consecutiveWins;   // Current winning streak
    private Integer consecutiveLosses; // Current losing streak
}
