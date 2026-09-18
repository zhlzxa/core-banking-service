package io.github.zhlzxa.corebanking.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * HTTP security for the API.
 *
 * <p>Every endpoint except the probes, info and metrics requires a valid bearer token. Those three
 * are served on the management port, which is reachable only from inside the cluster, and expose
 * no customer data. Fine-grained
 * authorization (scope, role and resource ownership) is enforced in the service layer, close to the
 * operations it protects, so that it cannot be bypassed by adding a new endpoint.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            BankJwtAuthenticationConverter jwtConverter,
            ProblemAuthenticationEntryPoint authenticationEntryPoint,
            ProblemAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                // The API is stateless and authenticates with bearer tokens in the Authorization
                // header. There is no session cookie a browser would attach automatically, so
                // cross-site request forgery has nothing to exploit.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Machines and people are kept apart at the URL level: terminals may only call the
                // terminal endpoints, and those endpoints accept terminals only.
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus")
                        .permitAll()
                        // The API description, when enabled, documents the API but grants nothing:
                        // every operation it describes still requires a token.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        .requestMatchers("/atm/**")
                        .hasRole(TerminalPrincipal.ROLE)
                        .anyRequest()
                        .access(AuthorizationManagers.allOf(
                                AuthenticatedAuthorizationManager.authenticated(),
                                AuthorizationManagers.not(
                                        AuthorityAuthorizationManager.hasRole(TerminalPrincipal.ROLE)))))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
