package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteTradeRequest {
    private String pair;
    private String side; // BUY or SELL
    private Double entry;
    private Double amount; // USD amount for position
    private Double quantity;
    private Integer leverage;
    private Double stopLoss;
    private Double tp1;
    private Double tp2;
    private Double tp3;
    private Double tp4;
    private Long signalId; // Optional: link to original signal
    private Long userId; // User ID (will be set by controller)
}
