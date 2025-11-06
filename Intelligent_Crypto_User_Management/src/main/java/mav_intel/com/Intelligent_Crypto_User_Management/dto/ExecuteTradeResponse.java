package mav_intel.com.Intelligent_Crypto_User_Management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecuteTradeResponse {
    private Long tradeId;
    private String pair;
    private String status; // SUCCESS, FAILED, PENDING
    private String orderId; // Binance order ID
    private String message;
    private Double executedPrice;
    private Long timestamp;

    public ExecuteTradeResponse(Long tradeId, String pair, String status, String message) {
        this.tradeId = tradeId;
        this.pair = pair;
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }
}
