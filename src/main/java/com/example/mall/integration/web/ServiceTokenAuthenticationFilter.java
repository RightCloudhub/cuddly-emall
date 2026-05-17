package com.example.mall.integration.web;

import com.example.mall.integration.AskFlowProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates a static {@code Authorization: Bearer <service-token>} on the AskFlow → mall direction
 * (order lookup I1, ticket callback I3, loyalty I6). The token is the value of {@code
 * MALL_ASKFLOW_SERVICE_TOKEN} — same value the mall presents when calling AskFlow. Comparison is
 * constant-time.
 *
 * <p>If the configured service token is empty (development default), the filter rejects everything
 * — fail-closed. The filter is wired only into the integration security chain (see {@link
 * IntegrationSecurityConfig}); a {@code FilterRegistrationBean} in that config disables Spring
 * Boot's automatic global registration.
 */
@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private static final String SERVICE_PRINCIPAL = "askflow-service";

    private final AskFlowProperties props;

    public ServiceTokenAuthenticationFilter(AskFlowProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader(HttpHeaders.AUTHORIZATION);
        String expected = props.getServiceToken();
        if (header != null && header.startsWith(BEARER) && !expected.isEmpty()) {
            String presented = header.substring(BEARER.length()).trim();
            if (constantTimeEquals(presented, expected)) {
                var auth =
                        new UsernamePasswordAuthenticationToken(
                                SERVICE_PRINCIPAL,
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_SERVICE")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(req, res);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
