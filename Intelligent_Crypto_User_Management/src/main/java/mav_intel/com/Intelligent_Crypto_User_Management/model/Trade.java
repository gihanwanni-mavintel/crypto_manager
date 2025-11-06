package mav_intel.com.Intelligent_Crypto_User_Management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "trades")
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "signal_id")
    private Long signalId;

    @Column(name = "pair", nullable = false)
    private String pair;

    @Column(name = "side", nullable = false)
    private String side; // BUY or SELL

    @Column(name = "leverage")
    private Integer leverage;

    @Column(name = "entry_price")
    private Double entryPrice;

    @Column(name = "entry_quantity")
    private Double entryQuantity;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "tp1")
    private Double tp1;

    @Column(name = "tp2")
    private Double tp2;

    @Column(name = "tp3")
    private Double tp3;

    @Column(name = "tp4")
    private Double tp4;

    @Column(name = "status")
    private String status; // OPEN, CLOSED, PARTIAL, STOPPED_OUT, LIQUIDATED

    @Column(name = "binance_order_id")
    private String binanceOrderId;

    @Column(name = "binance_position_id")
    private String binancePositionId;

    @Column(name = "opened_at")
    private OffsetDateTime openedAt;

    @Column(name = "closed_at")
    private OffsetDateTime closedAt;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "pnl")
    private Double pnl; // Profit/Loss in USDT

    @Column(name = "pnl_percent")
    private Double pnlPercent; // Profit/Loss percentage

    @Column(name = "exit_reason")
    private String exitReason; // TP1, TP2, TP3, TP4, SL, MANUAL, etc

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (openedAt == null) {
            openedAt = OffsetDateTime.now();
        }
    }
}
