package com.example.mall.integration.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security chain dedicated to service-to-service integration endpoints. Ordered before the main
 * chain so it claims the matching paths.
 *
 * <p>Excluded path: {@code /api/v1/integration/auth/bridge} — that endpoint authenticates a real
 * mall user JWT (the user is "trading" their mall token for an AskFlow token), so the main chain
 * handles it.
 */
@Configuration
public class IntegrationSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain integrationServiceTokenChain(
            HttpSecurity http, ServiceTokenAuthenticationFilter filter) throws Exception {
        http.securityMatcher(
                        "/api/v1/integration/orders/**",
                        "/api/v1/integration/tickets/**",
                        "/api/v1/integration/loyalty/**")
                .csrf(c -> c.disable())
                .cors(c -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        a -> a.anyRequest().hasRole("SERVICE"))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        ex ->
                                ex.authenticationEntryPoint(
                                                (req, res, e) ->
                                                        writeError(
                                                                res,
                                                                HttpStatus.UNAUTHORIZED,
                                                                "service_token_required",
                                                                "valid service bearer token required"))
                                        .accessDeniedHandler(
                                                (req, res, e) ->
                                                        writeError(
                                                                res,
                                                                HttpStatus.FORBIDDEN,
                                                                "forbidden",
                                                                "service role required")));
        return http.build();
    }

    private static void writeError(
            HttpServletResponse res, HttpStatus status, String code, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body =
                String.format(
                        "{\"code\":\"%s\",\"message\":\"%s\",\"details\":[],\"timestamp\":\"%s\"}",
                        code, message, Instant.now());
        res.getWriter().write(body);
    }

    /**
     * Spring Boot auto-registers any servlet {@link jakarta.servlet.Filter} bean into the global
     * filter chain. We want the service-token filter to fire ONLY inside the integration
     * security chain (via {@code addFilterBefore}), so disable the global registration here.
     */
    @Bean
    public FilterRegistrationBean<ServiceTokenAuthenticationFilter>
            disableServiceTokenAutoRegistration(ServiceTokenAuthenticationFilter filter) {
        FilterRegistrationBean<ServiceTokenAuthenticationFilter> reg =
                new FilterRegistrationBean<>(filter);
        reg.setEnabled(false);
        return reg;
    }
}
