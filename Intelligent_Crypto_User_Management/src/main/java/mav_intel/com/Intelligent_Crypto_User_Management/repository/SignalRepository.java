package mav_intel.com.Intelligent_Crypto_User_Management.repository;

import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignalRepository extends JpaRepository<Signal, Long> {
    // Find signals by pair (e.g., BTCUSDT)
    List<Signal> findByPair(String pair);

    // Find signals by setup type
    List<Signal> findBySetupType(String setupType);

    // Find all signals sorted by newest first (by id descending)
    List<Signal> findAllByOrderByIdDesc();

    // Find signals by channel
    List<Signal> findByChannel(String channel);
}
