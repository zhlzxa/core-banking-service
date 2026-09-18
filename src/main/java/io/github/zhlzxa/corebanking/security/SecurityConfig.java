package io.github.zhlzxa.corebanking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security for the API.
 *
 * <p>Every endpoint except health and info requires a valid bearer token. Fine-grained
 * authorization (scope, role and resource ownership) is enforced in the service layer, close to the
 * operations it protects, so that it cannot be bypassed by adding a new endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, BankJwtAuthenticationConverter jwtConverter)
            throws Exception {
        http
                // The API is stateless and authenticates with bearer tokens in the Authorization
                // header. There is no session cookie a browser would attach automatically, so
                // cross-site request forgery has nothing to exploit.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter)));
        return http.build();
    }
}
