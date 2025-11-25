package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for trade filtering parameters
 * Allows filtering trades by various criteria
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeFilterDTO {

    // ============ PAGINATION ============
    private Integer page = 0;          // Page number (0-indexed)
    private Integer pageSize = 20;     // Trades per page

    // ============ SYMBOL FILTER ============
    private String symbol;             // e.g., "BTCUSDT", "ETHUSD"

    // ============ DATE RANGE FILTER ============
    private LocalDate fromDate;        // Start date (inclusive)
    private LocalDate toDate;          // End date (inclusive)

    // ============ P&L FILTER ============
    private BigDecimal pnlMin;         // Minimum P&L amount
    private BigDecimal pnlMax;         // Maximum P&L amount
    private BigDecimal pnlPercentMin;  // Minimum P&L percentage
    private BigDecimal pnlPercentMax;  // Maximum P&L percentage

    // ============ SIDE FILTER ============
    private String side;               // "BUY" or "SELL" (or null for both)

    // ============ STATUS FILTER ============
    private String status;             // "CLOSED", "OPEN", "PARTIAL", etc.

    // ============ EXIT REASON FILTER ============
    private String exitReason;         // "TP1", "TP2", "TP3", "TP4", "SL", "MANUAL"

    // ============ SORTING ============
    private String sortBy = "closedAt"; // Field to sort by (closedAt, pnl, pnlPercent, etc.)
    private String sortOrder = "DESC";  // "ASC" or "DESC"
}
