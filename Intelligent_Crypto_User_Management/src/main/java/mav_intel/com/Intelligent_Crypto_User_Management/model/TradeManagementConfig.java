package mav_intel.com.Intelligent_Crypto_User_Management.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "trade_management_config")
@Data
public class TradeManagementConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "margin_mode", nullable = false)
    private String marginMode = "ISOLATED"; // ISOLATED or CROSS

    @Column(name = "max_leverage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxLeverage = new BigDecimal("20.00");

    @Column(name = "max_position_size_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPositionSizePercent = new BigDecimal("10.00");

    @Column(name = "max_concurrent_positions", nullable = false)
    private Integer maxConcurrentPositions = 5;

    @Column(name = "risk_percentage_per_trade", nullable = false, precision = 5, scale = 2)
    private BigDecimal riskPercentagePerTrade = new BigDecimal("2.00");

    @Column(name = "tp1_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal tp1Percentage = new BigDecimal("25.00");

    @Column(name = "tp2_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal tp2Percentage = new BigDecimal("25.00");

    @Column(name = "tp3_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal tp3Percentage = new BigDecimal("25.00");

    @Column(name = "tp4_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal tp4Percentage = new BigDecimal("25.00");

    @Column(name = "num_tp_levels", nullable = false)
    private Integer numTpLevels = 4;

    @Column(name = "enable_trailing_stop", nullable = false)
    private Boolean enableTrailingStop = false;

    @Column(name = "trailing_stop_percent", precision = 5, scale = 2)
    private BigDecimal trailingStopPercent;

    @Column(name = "breakeven_profit_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal breakevenProfitPercent = new BigDecimal("1.00");

    @Column(name = "enable_profit_reallocation", nullable = false)
    private Boolean enableProfitReallocation = false;

    @Column(name = "profit_reallocation_percent", precision = 5, scale = 2)
    private BigDecimal profitReallocationPercent;

    @Column(name = "order_type", nullable = false)
    private String orderType = "LIMIT";

    @Column(name = "time_in_force", nullable = false)
    private String timeInForce = "GTC";

    @Column(name = "auto_execute_trades", nullable = false)
    private Boolean autoExecuteTrades = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
