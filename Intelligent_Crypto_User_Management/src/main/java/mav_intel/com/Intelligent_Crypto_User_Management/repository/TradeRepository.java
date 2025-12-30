package mav_intel.com.Intelligent_Crypto_User_Management.repository;

import mav_intel.com.Intelligent_Crypto_User_Management.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {
    List<Trade> findByStatus(String status);
    List<Trade> findByStatusAndUserId(String status, Long userId);
    List<Trade> findByUserId(Long userId);
    List<Trade> findByUserIdOrderByOpenedAtDesc(Long userId);
    List<Trade> findByPair(String pair);
    List<Trade> findBySignalId(Long signalId);
    List<Trade> findAllByOrderByOpenedAtDesc();
    Optional<Trade> findByBinanceOrderId(String binanceOrderId);
    List<Trade> findByPairAndStatus(String pair, String status);
}
