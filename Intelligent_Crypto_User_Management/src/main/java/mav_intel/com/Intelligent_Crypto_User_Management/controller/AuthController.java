package mav_intel.com.Intelligent_Crypto_User_Management.controller;

import mav_intel.com.Intelligent_Crypto_User_Management.model.Role;
import mav_intel.com.Intelligent_Crypto_User_Management.model.User;
import mav_intel.com.Intelligent_Crypto_User_Management.repository.UserRepository;
import mav_intel.com.Intelligent_Crypto_User_Management.security.JwtUtil;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil,
                          UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> loginRequest) {
        String username = loginRequest.get("username");
        String password = loginRequest.get("password");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        String role = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst().orElse("ROLE_USER");

        String token = jwtUtil.generateToken(username, role);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        return response;
    }
    @GetMapping("/health")
    public String health() {
        return "✅ Spring Boot app is running!";
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> registerRequest) {
        String username = registerRequest.get("username");
        String password = registerRequest.get("password");

        Map<String, Object> response = new HashMap<>();

        // Check if user already exists
        if (userRepository.findByUsername(username).isPresent()) {
            response.put("status", "error");
            response.put("message", "Username already exists");
            return response;
        }

        // Validate input
        if (username == null || username.trim().isEmpty() ||
            password == null || password.trim().isEmpty()) {
            response.put("status", "error");
            response.put("message", "Username and password are required");
            return response;
        }

        // Create new user
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password)); // Hash the password
        user.setRole(Role.USER);

        userRepository.save(user);

        response.put("status", "success");
        response.put("message", "User registered successfully. You can now login.");
        return response;
    }

}
