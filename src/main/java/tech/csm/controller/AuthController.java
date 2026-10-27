package tech.csm.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tech.csm.config.JwtService;
import tech.csm.dto.LoginRequest;
import tech.csm.dto.LoginResponse;
import tech.csm.model.User;
import tech.csm.repository.UserRepo;

@RestController
@RequestMapping("/auth")
public class AuthController {
	
	private UserRepo userrepo;
	
	private PasswordEncoder passwordencoder;
	
	private JwtService jwtservice;
	
	 public AuthController(UserRepo userRepository, PasswordEncoder passwordEncoder,
             JwtService jwtService) {
			this.userrepo = userRepository;
			this.passwordencoder = passwordEncoder;
			this.jwtservice = jwtService;
			}
	 
	 @PostMapping("/login")
	    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
	        User user = userrepo.findByUserName(req.username()).orElse(null);

	        // same message for "user not found" and "wrong password"
	        if (user == null || !passwordencoder.matches(req.password(), user.getPassword())) {
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body(Map.of("message", "Invalid username or password"));
	        }

	        String token = jwtservice.generateToken(user.getUserName(), user.getRole());
	        return ResponseEntity.ok(new tech.csm.dto.LoginResponse(token, "Bearer",
	                jwtservice.getExpirationMs(), user.getUserName(), user.getFullName(), user.getRole()));
	    }

}
