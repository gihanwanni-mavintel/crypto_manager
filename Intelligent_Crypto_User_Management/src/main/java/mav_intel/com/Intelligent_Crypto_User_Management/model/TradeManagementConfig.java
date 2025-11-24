package mav_intel.com.Intelligent_Crypto_User_Management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Builder
@Table(name = "trade_management_config")
@NoArgsConstructor
@AllArgsConstructor
public class TradeManagementConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", unique = true, nullable = false)
    private Long userId;

    // ============ MARGIN & LEVERAGE ============
    @Column(name = "margin_mode", length = 20, nullable = false)
    @Default
    private String marginMode = "ISOLATE";

    @Column(name = "max_leverage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal maxLeverage = BigDecimal.valueOf(20.00);

    // ============ POSITION SIZING ============
    @Column(name = "max_position_size_percent", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal maxPositionSizePercent = BigDecimal.valueOf(10.00);

    @Column(name = "max_concurrent_positions", nullable = false)
    @Default
    private Integer maxConcurrentPositions = 5;

    @Column(name = "risk_percentage_per_trade", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal riskPercentagePerTrade = BigDecimal.valueOf(2.00);

    // ============ TAKE PROFIT LEVELS ============
    @Column(name = "tp1_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal tp1Percentage = BigDecimal.valueOf(25.00);

    @Column(name = "tp2_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal tp2Percentage = BigDecimal.valueOf(25.00);

    @Column(name = "tp3_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal tp3Percentage = BigDecimal.valueOf(25.00);

    @Column(name = "tp4_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal tp4Percentage = BigDecimal.valueOf(25.00);

    @Column(name = "num_tp_levels", nullable = false)
    @Default
    private Integer numTPLevels = 4;

    // ============ STOP LOSS SETTINGS ============
    @Column(name = "enable_trailing_stop", nullable = false)
    @Default
    private Boolean enableTrailingStop = false;

    @Column(name = "trailing_stop_percent", columnDefinition = "DECIMAL(5,2)")
    private BigDecimal trailingStopPercent;

    @Column(name = "breakeven_profit_percent", nullable = false, columnDefinition = "DECIMAL(5,2)")
    @Default
    private BigDecimal breakevenProfitPercent = BigDecimal.valueOf(1.00);

    // ============ PROFIT REALLOCATION ============
    @Column(name = "enable_profit_reallocation", nullable = false)
    @Default
    private Boolean enableProfitReallocation = false;

    @Column(name = "profit_reallocation_percent", columnDefinition = "DECIMAL(5,2)")
    private BigDecimal profitReallocationPercent;

    // ============ ORDER PREFERENCES ============
    @Column(name = "order_type", length = 20, nullable = false)
    @Default
    private String orderType = "LIMIT";

    @Column(name = "time_in_force", length = 20, nullable = false)
    @Default
    private String timeInForce = "GTC";

    // ============ AUTO-EXECUTION ============
    @Column(name = "auto_execute_trades", nullable = false)
    @Default
    private Boolean autoExecuteTrades = true;

    // ============ METADATA ============
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ============ HELPER METHODS ============

    /**
     * Validate that TP percentages sum to 100
     */
    public boolean validateTPPercentages() {
        BigDecimal total = tp1Percentage.add(tp2Percentage)
            .add(tp3Percentage).add(tp4Percentage);
        return total.equals(BigDecimal.valueOf(100.00));
    }

    /**
     * Get active TP percentages based on numTPLevels
     */
    public BigDecimal[] getActiveTPPercentages() {
        BigDecimal[] percentages = new BigDecimal[numTPLevels];
        percentages[0] = tp1Percentage;
        if (numTPLevels > 1) percentages[1] = tp2Percentage;
        if (numTPLevels > 2) percentages[2] = tp3Percentage;
        if (numTPLevels > 3) percentages[3] = tp4Percentage;
        return percentages;
    }

    // ============ LEGACY FIELD MAPPING (for backward compatibility) ============
    // Old field names mapped to new field names for existing code
    @Transient
    public void setMaxPositionSize(BigDecimal value) {
        this.maxPositionSizePercent = value;
    }

    @Transient
    public BigDecimal getMaxPositionSize() {
        return this.maxPositionSizePercent;
    }

    @Transient
    public void setTp1ExitPercentage(BigDecimal value) {
        this.tp1Percentage = value;
    }

    @Transient
    public BigDecimal getTp1ExitPercentage() {
        return this.tp1Percentage;
    }

    @Transient
    public void setTp2ExitPercentage(BigDecimal value) {
        this.tp2Percentage = value;
    }

    @Transient
    public BigDecimal getTp2ExitPercentage() {
        return this.tp2Percentage;
    }

    @Transient
    public void setTp3ExitPercentage(BigDecimal value) {
        this.tp3Percentage = value;
    }

    @Transient
    public BigDecimal getTp3ExitPercentage() {
        return this.tp3Percentage;
    }

    @Transient
    public void setTp4ExitPercentage(BigDecimal value) {
        this.tp4Percentage = value;
    }

    @Transient
    public BigDecimal getTp4ExitPercentage() {
        return this.tp4Percentage;
    }
}
