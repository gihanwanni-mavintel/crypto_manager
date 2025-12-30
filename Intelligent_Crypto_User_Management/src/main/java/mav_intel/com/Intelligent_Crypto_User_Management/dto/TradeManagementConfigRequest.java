package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class TradeManagementConfigRequest {
    private Long userId;
    private String marginMode; // ISOLATED or CROSSED
    private BigDecimal maxLeverage;
    private BigDecimal maxPositionSizePercent;
}
