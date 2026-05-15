package com.nsemcx.trading.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security configuration for the NSE-MCX Trading backend.
 *
 * <p>Access rules:
 * <ul>
 *   <li>/api/**               – fully open (REST endpoints, consumed by SPA)</li>
 *   <li>/ws/**                – fully open (WebSocket/SockJS handshake)</li>
 *   <li>/actuator/health      – fully open (liveness/readiness probes)</li>
 *   <li>/actuator/info        – fully open</li>
 *   <li>/actuator/**          – HTTP Basic (admin monitoring)</li>
 *   <li>everything else       – HTTP Basic</li>
 * </ul>
 *
 * <p>CSRF is disabled because the API is stateless (no session cookies).
 * Session creation is STATELESS – no HttpSession is ever created.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Comma-separated list of allowed origins, e.g. "http://localhost:3000,https://prod.example.com". */
    @Value("${security.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String allowedOriginsValue;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // ── CORS ──────────────────────────────────────────────────────────────
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF ──────────────────────────────────────────────────────────────
            // Disabled: REST + WebSocket API; no browser-cookie authentication
            .csrf(AbstractHttpConfigurer::disable)

            // ── Session ───────────────────────────────────────────────────────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization rules ───────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Public REST API – consumed by React SPA
                .requestMatchers("/api/**").permitAll()
                // WebSocket STOMP endpoint (SockJS upgrade requests)
                .requestMatchers("/ws/**").permitAll()
                // Actuator probes used by load balancers / Docker health checks
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // All other actuator endpoints require HTTP Basic (admin)
                .requestMatchers("/actuator/**").hasRole("ACTUATOR")
                // Catch-all: require authentication
                .anyRequest().authenticated()
            )

            // ── HTTP Basic for actuator / non-API endpoints ───────────────────────
            .httpBasic(basic -> basic
                .realmName("NSE-MCX Trading API"));

        return http.build();
    }

    /**
     * CORS configuration permitting the React dev-server (localhost:3000 / 5173)
     * plus any additional origins supplied via {@code security.cors.allowed-origins}.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Build allowed-origins list from property + hardcoded defaults
        List<String> origins = Arrays.stream(allowedOriginsValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept",
                "X-Requested-With", "Cache-Control", "Origin"
        ));
        configuration.setExposedHeaders(List.of("X-Total-Count", "X-Request-Id"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L); // pre-flight cache 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
