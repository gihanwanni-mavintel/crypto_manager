package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import mav_intel.com.Intelligent_Crypto_User_Management.model.Signal;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.SignalRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final SignalRepository signalRepository;

    public SignalController(SignalRepository signalRepository) {
        this.signalRepository = signalRepository;
    }

    /**
     * Get all signals sorted by newest first
     */
    @GetMapping
    public List<Signal> getAllSignals() {
        return signalRepository.findAllByOrderByIdDesc();
    }

    /**
     * Get signals by trading pair (e.g., BTCUSDT)
     */
    @GetMapping("/pair/{pair}")
    public List<Signal> getSignalsByPair(@PathVariable String pair) {
        return signalRepository.findByPair(pair);
    }

    /**
     * Get signals by setup type
     */
    @GetMapping("/setup/{setupType}")
    public List<Signal> getSignalsBySetupType(@PathVariable String setupType) {
        return signalRepository.findBySetupType(setupType);
    }

    /**
     * Get signals by channel
     */
    @GetMapping("/channel/{channel}")
    public List<Signal> getSignalsByChannel(@PathVariable String channel) {
        return signalRepository.findByChannel(channel);
    }

    /**
     * Get signal by ID
     */
    @GetMapping("/{id}")
    public Signal getSignalById(@PathVariable Long id) {
        return signalRepository.findById(id).orElseThrow(() -> new RuntimeException("Signal not found"));
    }

    /**
     * Create a new signal
     */
    @PostMapping
    public Signal createSignal(@RequestBody Signal signal) {
        return signalRepository.save(signal);
    }

    /**
     * Update an existing signal
     */
    @PutMapping("/{id}")
    public Signal updateSignal(@PathVariable Long id, @RequestBody Signal signalDetails) {
        return signalRepository.findById(id).map(signal -> {
            signal.setPair(signalDetails.getPair());
            signal.setSetupType(signalDetails.getSetupType());
            signal.setEntry(signalDetails.getEntry());
            signal.setLeverage(signalDetails.getLeverage());
            signal.setTp1(signalDetails.getTp1());
            signal.setTp2(signalDetails.getTp2());
            signal.setTp3(signalDetails.getTp3());
            signal.setTp4(signalDetails.getTp4());
            signal.setStopLoss(signalDetails.getStopLoss());
            signal.setTimestamp(signalDetails.getTimestamp());
            signal.setFullMessage(signalDetails.getFullMessage());
            signal.setChannel(signalDetails.getChannel());
            signal.setQuantity(signalDetails.getQuantity());
            return signalRepository.save(signal);
        }).orElseThrow(() -> new RuntimeException("Signal not found"));
    }

    /**
     * Delete a signal
     */
    @DeleteMapping("/{id}")
    public void deleteSignal(@PathVariable Long id) {
        signalRepository.deleteById(id);
    }
}
