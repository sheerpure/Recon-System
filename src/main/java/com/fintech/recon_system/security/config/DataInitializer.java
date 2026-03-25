package com.fintech.recon_system.security.config;

import com.fintech.recon_system.security.model.User;
import com.fintech.recon_system.security.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Check if admin exists to avoid duplicate entries on restart
            if (userRepository.findByUsername("admin").isEmpty()) {
                User admin = new User();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123")); // BCrypt encryption
                admin.setRoles(Set.of("ROLE_ADMIN"));
                userRepository.save(admin);
                System.out.println("✅ Security Initialization: Default Admin 'admin/admin123' created.");
            }
        };
    }
}