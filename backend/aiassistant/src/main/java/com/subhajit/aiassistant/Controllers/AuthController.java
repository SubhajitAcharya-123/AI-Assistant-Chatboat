package com.subhajit.aiassistant.Controllers;

import com.subhajit.aiassistant.DTO.AuthResponse;
import com.subhajit.aiassistant.DTO.LoginRequest;
import com.subhajit.aiassistant.DTO.RegisterRequest;
import com.subhajit.aiassistant.Entities.User;
import com.subhajit.aiassistant.Repository.UserRepository;
import com.subhajit.aiassistant.Services.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"https://ai-assistant-chatboat.vercel.app" , "http://localhost:3000"})
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "Error: Email is already in use!");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(errorResponse);
        }
        return ResponseEntity.ok(
                authService.register(request)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {

        return ResponseEntity.ok(
                authService.login(request)
        );
    }
    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        if (authentication == null) {
            return Map.of("username", "User");
        }
        User user = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow();

        return Map.of(
                "username", user.getUsername(),
                "email", user.getEmail()
        );
    }
}
