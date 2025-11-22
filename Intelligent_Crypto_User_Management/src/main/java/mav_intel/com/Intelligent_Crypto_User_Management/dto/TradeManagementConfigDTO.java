package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder.Default;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeManagementConfigDTO {

    private BigDecimal maxPositionSize;
    private BigDecimal maxLeverage;
    private BigDecimal tp1ExitPercentage;
    private BigDecimal tp2ExitPercentage;
    private BigDecimal tp3ExitPercentage;
    private BigDecimal tp4ExitPercentage;
    @Default
    private String marginMode = "ISOLATE";
}
