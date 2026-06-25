package com.library.helper;

import com.library.entity.User;
import com.library.entity.enums.UserRole;
import com.library.repository.UserRepository;
import com.library.security.JwtProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class TestAuthHelper {

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Create an admin user and return a valid JWT Bearer token.
     */
    public String adminToken() {
        User user = userRepository.findByUsername("admin").orElseGet(() -> {
            User u = new User();
            u.setUsername("admin");
            u.setPassword(passwordEncoder.encode("admin"));
            u.setRole(UserRole.ADMIN);
            return userRepository.save(u);
        });
        return "Bearer " + jwtProvider.generateToken(user);
    }

    /**
     * Create a reader user with the given studentId and readerId, return JWT Bearer token.
     */
    public String readerToken(String studentId, Long readerId) {
        User user = userRepository.findByUsername(studentId).orElseGet(() -> {
            User u = new User();
            u.setUsername(studentId);
            u.setPassword(passwordEncoder.encode("123456"));
            u.setRole(UserRole.READER);
            u.setReaderId(readerId);
            return userRepository.save(u);
        });
        // Ensure readerId is set (in case user already existed without it)
        if (user.getReaderId() == null) {
            user.setReaderId(readerId);
            userRepository.save(user);
        }
        return "Bearer " + jwtProvider.generateToken(user);
    }
}
