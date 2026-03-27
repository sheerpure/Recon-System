package com.fintech.recon_system.config;

import com.fintech.recon_system.model.User;
import com.fintech.recon_system.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataInitializer.class);

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("🚀 No users found. Initializing default admin account...");
            
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            
            // Assign default administration role
            Set<String> roles = new HashSet<>();
            roles.add("ROLE_ADMIN");
            admin.setRoles(roles);
            
            userRepository.save(admin);
            log.info("✅ Default admin account created: [admin / admin123]");
        } else {
            log.info("ℹ️ Users already exist. Skipping data initialization.");
        }
    }
}