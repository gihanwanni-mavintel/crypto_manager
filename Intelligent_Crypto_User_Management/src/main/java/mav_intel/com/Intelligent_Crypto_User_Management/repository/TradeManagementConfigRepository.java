package mav_intel.com.Intelligent_Crypto_User_Management.repository;

import mav_intel.com.Intelligent_Crypto_User_Management.model.TradeManagementConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TradeManagementConfigRepository extends JpaRepository<TradeManagementConfig, Long> {
    Optional<TradeManagementConfig> findByUserId(Long userId);
}
