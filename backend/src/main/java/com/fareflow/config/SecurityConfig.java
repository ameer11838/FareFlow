package com.fareflow.config;

import com.fareflow.auth.AuthProperties;
import com.fareflow.auth.JwtAuthenticationFilter;
import com.fareflow.auth.JwtProperties;
import com.fareflow.auth.JwtService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

/**
 * Security wiring for both modes.
 *
 * <p><strong>Auth mode</strong> ({@code fareflow.auth.enabled=true}): stateless JWT.
 * Only the auth endpoints, health, and the route catalog are public; everything
 * touching a user's money requires a verified token.
 *
 * <p><strong>Demo mode</strong> ({@code enabled=false}): the filter chain permits
 * everything, and {@code CurrentUserService} resolves the single seeded demo user.
 * No {@code JwtService} bean is created, so {@code JWT_SECRET} is not needed to run
 * the demo — which is the point of the flag.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({AuthProperties.class, JwtProperties.class})
public class SecurityConfig {

    /**
     * Only registered when auth is on — a {@code @Bean} method returning null
     * registers nothing, which then fails injection, so the condition has to be on
     * the bean definition rather than inside the method.
     *
     * <p>Constructing it validates the secret, so a missing or weak
     * {@code JWT_SECRET} fails startup instead of silently signing tokens with a
     * guessable key. Demo mode never reaches this, which is why the demo runs
     * without a secret at all.
     */
    @Bean
    @ConditionalOnProperty(name = "fareflow.auth.enabled", havingValue = "true", matchIfMissing = true)
    public JwtService jwtService(JwtProperties jwtProperties, Clock clock) {
        return new JwtService(jwtProperties.secret(), jwtProperties.expirationSeconds(), clock);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with the Spring default strength (10). Plaintext is never stored.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AuthProperties authProperties,
                                                   ObjectProvider<JwtService> jwtService) throws Exception {
        http
                // Stateless JWT API with no cookies, so CSRF has nothing to protect.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (!authProperties.enabled()) {
            // Demo mode: nothing is gated. Identity still comes from the server.
            http.authorizeHttpRequests(requests -> requests.anyRequest().permitAll());
            return http.build();
        }

        http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/config").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        // The transit catalog and route scoring carry no personal
                        // data, so they stay open. Nothing here reads a user's money.
                        .requestMatchers("/api/transit-routes/**", "/api/transit/coverage",
                                "/api/transit/stops/**",
                                "/api/recommendations/**").permitAll()
                        // The onboarding vocabulary is a static catalogue with no
                        // personal data in it, exactly like the profile catalogue
                        // above. The profile itself, one path segment up, is not.
                        .requestMatchers("/api/profile/options").permitAll()
                        .requestMatchers("/api/locations/**").permitAll()
                        // Searching is public; taking a journey moves money, so it is not.
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/journeys").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService.getObject()),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(handling -> handling
                        // 401 for "who are you", 403 for "not yours" -- Spring's
                        // defaults would return 403 for both.
                        .authenticationEntryPoint((request, response, exception) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED,
                                        "Authentication required",
                                        "This endpoint requires a valid bearer token"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeProblem(response, HttpStatus.FORBIDDEN,
                                        "Forbidden",
                                        "You do not have access to this resource")));

        return http.build();
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response,
                                     HttpStatus status, String title, String detail)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(title, status.value(), detail));
    }
}
