package com.luxesuite.api.config;

import com.luxesuite.api.security.JwtAuthenticationFilter;
import com.luxesuite.api.security.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import com.luxesuite.api.security.CsrfCookieFilter;
import com.luxesuite.api.security.RateLimitFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CsrfCookieFilter csrfCookieFilter;
    private final Environment env;

    @Value("${jwt.secret:dGhpcy1pcy1hLXZlcnktc2VjdXJlLWp3dC1zZWNyZXQta2V5LXRoYXQtaXMtMjU2LWJpdHM=}")
    private String jwtSecret;

    @Value("${razorpay.key.secret:eZyRCiqqgfbu1lNVbB60CAFH}")
    private String razorpaySecret;

    @Value("${stripe.webhook.secret:dummy_stripe_webhook_secret}")
    private String stripeWebhookSecret;

    @Value("${razorpay.webhook-secret:dummy_webhook_secret}")
    private String razorpayWebhookSecret;

    @Bean
    public RateLimitFilter rateLimitFilter() {
        return new RateLimitFilter();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            CustomUserDetailsService customUserDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(customUserDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @PostConstruct
    public void checkSecrets() {
        if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            if ("dGhpcy1pcy1hLXZlcnktc2VjdXJlLWp3dC1zZWNyZXQta2V5LXRoYXQtaXMtMjU2LWJpdHM=".equals(jwtSecret)) {
                throw new IllegalStateException("FATAL: Burned JWT secret used in production!");
            }
            if ("eZyRCiqqgfbu1lNVbB60CAFH".equals(razorpaySecret)) {
                throw new IllegalStateException("FATAL: Burned Razorpay secret used in production!");
            }
            if ("dummy_stripe_webhook_secret".equals(stripeWebhookSecret) || "whsec_test_secret".equals(stripeWebhookSecret)) {
                throw new IllegalStateException("FATAL: Dummy Stripe webhook secret used in production!");
            }
            if ("dummy_webhook_secret".equals(razorpayWebhookSecret)) {
                throw new IllegalStateException("FATAL: Dummy Razorpay webhook secret used in production!");
            }
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authenticationProvider) throws Exception {
        if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                    "/api/v1/auth/**",
                    "/api/v1/payments/stripe/webhook",
                    "/api/v1/payments/razorpay/webhook"
                )
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'; script-src 'self'; frame-src 'none'; object-src 'none';"))
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**", "/error").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/v1/public/**", "/api/v1/cms/**", "/api/v1/content/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/services").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/services/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/reviews", "/api/v1/reviews/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/appointments").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/payments/stripe/create-deposit-intent/**").permitAll()
                .requestMatchers("/api/v1/events/stream").permitAll()
                .requestMatchers("/api/v1/payments/stripe/webhook").permitAll()
                .requestMatchers("/api/v1/payments/razorpay/webhook").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED))
            )
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitFilter(), BasicAuthenticationFilter.class)
            .addFilterAfter(csrfCookieFilter, BasicAuthenticationFilter.class);

        return http.build();
    }

    @Value("${cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
