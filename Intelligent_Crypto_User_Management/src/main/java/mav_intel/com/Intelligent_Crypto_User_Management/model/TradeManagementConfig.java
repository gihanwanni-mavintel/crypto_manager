package mav_intel.com.Intelligent_Crypto_User_Management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    @Column(name = "max_position_size", nullable = false, columnDefinition = "DECIMAL(18,2)")
    private BigDecimal maxPositionSize;

    @Column(name = "max_leverage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal maxLeverage;

    @Column(name = "tp1_exit_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal tp1ExitPercentage;

    @Column(name = "tp2_exit_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal tp2ExitPercentage;

    @Column(name = "tp3_exit_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal tp3ExitPercentage;

    @Column(name = "tp4_exit_percentage", nullable = false, columnDefinition = "DECIMAL(5,2)")
    private BigDecimal tp4ExitPercentage;

    @Column(name = "margin_mode", length = 20)
    @Default
    private String marginMode = "ISOLATE";

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
