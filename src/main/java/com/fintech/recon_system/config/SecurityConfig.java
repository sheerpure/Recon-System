package com.fintech.recon_system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        System.out.println("✅ SecurityConfig: Applying Roles & Permissions...");
        
        http
            .csrf(AbstractHttpConfigurer::disable) //  開發階段暫時禁用 CSRF 以利 API 測試
            .authorizeHttpRequests(auth -> auth
                // 1. 開放靜態資源與登入頁
                .requestMatchers("/login", "/css/**", "/js/**", "/images/**").permitAll()
                
                // 2. 嚴格權限控管：只有 ADMIN 可以執行寫入動作 (上傳與審核)
                .requestMatchers("/api/transactions/upload/**").hasRole("ADMIN")
                .requestMatchers("/api/transactions/*/status").hasRole("ADMIN")
                .requestMatchers("/api/transactions/batch-status").hasRole("ADMIN")
                
                // 3. 其他所有請求 (例如首頁 Dashboard) 只要登入即可查看
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/login?logout")
                .deleteCookies("JSESSIONID")
                .permitAll()
            );
            
        return http.build();
    }

    //  建立內建測試帳號 
    @Bean
    public UserDetailsService userDetailsService() {
        // 管理員：可以上傳、可以審核
        UserDetails admin = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123"))
            .roles("ADMIN")
            .build();

        // 一般用戶：只能看 Dashboard 數據
        UserDetails user = User.builder()
            .username("user")
            .password(passwordEncoder().encode("user123"))
            .roles("USER")
            .build();

        return new InMemoryUserDetailsManager(admin, user);
    }

    //  使用 BCrypt 強制加密密碼
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}