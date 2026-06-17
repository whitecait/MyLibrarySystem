package com.library.config;

import com.library.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---- Public: infrastructure ----
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/h2-console/**",
                                "/error")
                                .permitAll()
                        // ---- Public: auth ----
                        .requestMatchers("/auth/login", "/auth/register")
                                .permitAll()
                        // ---- Public: book browsing ----
                        .requestMatchers(HttpMethod.GET, "/books/page",
                                "/books/detail/**")
                                .permitAll()
                        // ---- Public: category dropdown ----
                        .requestMatchers(HttpMethod.GET, "/categories/list")
                                .permitAll()
                        // ---- Admin: book management ----
                        .requestMatchers("/books/save", "/books/update", "/books/delete")
                                .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/books/info/**")
                                .hasRole("ADMIN")
                        // ---- Admin: category management (except list) ----
                        .requestMatchers("/categories/page",
                                "/categories/info/**",
                                "/categories/save",
                                "/categories/update",
                                "/categories/delete")
                                .hasRole("ADMIN")
                        // ---- Everything else requires authentication ----
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .headers(headers -> headers
                        .frameOptions(fo -> fo.sameOrigin())
                );

        return http.build();
    }
}
