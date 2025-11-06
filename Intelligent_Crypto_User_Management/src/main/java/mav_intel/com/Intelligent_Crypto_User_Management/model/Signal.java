package mav_intel.com.Intelligent_Crypto_User_Management.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.OffsetDateTime;

@Entity
@Data
@Table(name = "signal_messages")
@NoArgsConstructor
@AllArgsConstructor
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "pair", nullable = false)
    private String pair;

    @Column(name = "setup_type")
    private String setupType;

    @Column(name = "entry")
    private Double entry;

    @Column(name = "leverage")
    private Integer leverage;

    @Column(name = "tp1")
    private Double tp1;

    @Column(name = "tp2")
    private Double tp2;

    @Column(name = "tp3")
    private Double tp3;

    @Column(name = "tp4")
    private Double tp4;

    @Column(name = "stop_loss")
    private Double stopLoss;

    @Column(name = "timestamp")
    private OffsetDateTime timestamp;

    @Column(name = "full_message")
    private String fullMessage;

    @Column(name = "channel")
    private String channel;

    @Column(name = "quantity")
    private Double quantity;
}
