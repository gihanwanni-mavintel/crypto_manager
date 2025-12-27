package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteTradeRequest {
    private String pair;
    private String side; // LONG or SHORT
    private Double entry;
    private Double quantity;
    private Integer leverage; // Default: 20x
    private Double takeProfit;
    private Double stopLoss;
    private Long signalId; // Optional: link to original signal
}
