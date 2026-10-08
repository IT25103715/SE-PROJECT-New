package com.cinemahub.config;

import com.cinemahub.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central place where role-based access control is enforced (Function 1).
 * Rather than checking "if (user.getRole() == ...)" inside every controller,
 * Spring Security intercepts each request against the URL patterns below,
 * which keeps controllers thin and the security rules easy to audit in one
 * file.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * US-32: every staff-side role lands straight on its own dashboard after
     * login instead of the generic customer homepage, since that's their
     * entire scope on the site (or close to it). Only CUSTOMER (and anyone
     * with no matching role below) keeps the old "/" behaviour.
     */
    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            Set<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

            String redirectPath;
            if (roles.contains("ROLE_PROMOTION_MANAGER")) {
                redirectPath = "/promotion-manager/dashboard";
            } else if (roles.contains("ROLE_PAYMENT_MANAGER")) {
                redirectPath = "/payment-manager/dashboard";
            } else if (roles.contains("ROLE_SYSTEM_ADMIN") || roles.contains("ROLE_MANAGER")) {
                redirectPath = "/manager/movies-overview";
            } else if (roles.contains("ROLE_CINEMA_STAFF")) {
                redirectPath = "/staff/tickets";
            } else if (roles.contains("ROLE_CUSTOMER_RELATIONS_EXECUTIVE")) {
                redirectPath = "/cre/feedback";
            } else {
                redirectPath = "/";
            }
            response.sendRedirect(request.getContextPath() + redirectPath);
        };
    }

    /**
     * JSON API for the Android QR scanner app (TicketScanApiController) - checked BEFORE the
     * website chain below, and only for URLs under /api/**.
     *
     * The app sends HTTP Basic auth on every request ("Authorization: Basic base64(email:password)"),
     * checked against the same user accounts and AuthenticationProvider as the website login.
     * Stateless (no session cookie) and therefore no CSRF token - CSRF only protects cookie-based
     * browser sessions. A missing/wrong password gets 401 and a role that isn't allowed gets 403,
     * as plain HTTP status codes instead of redirects to the HTML login page.
     * No CORS config: CORS is enforced by web browsers only, and the Android app calls the API
     * directly (not from a web page), so it is not affected.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .authorizeHttpRequests(auth -> auth
                // Same roles as the web-based scanner ("/staff/**" below).
                .requestMatchers("/api/tickets/**").hasAnyRole("CINEMA_STAFF", "PAYMENT_MANAGER", "SYSTEM_ADMIN")
                .anyRequest().denyAll()
            )
            .httpBasic(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           @Value("${cinemahub.hsts.enabled:true}") boolean hstsEnabled,
                                           @Value("${cinemahub.remember-me.key}") String rememberMeKey) throws Exception {
        if (!hstsEnabled) {
            // Only switched off by the dev-only "https" profile: browsers would otherwise remember
            // "always use HTTPS" for the whole host (any port) and break plain http://localhost:8080.
            http.headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.disable()));
        }
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/home",
                        "/movies", "/movies/**",
                        // Public food & drinks menu (FoodMenuController) - linked from the homepage
                        // Promotions tab's food combo cards. Read-only.
                        "/menu",
                        "/showtimes", "/showtimes/**",
                        "/auth/**",
                        "/css/**", "/js/**", "/images/**",
                        "/error", "/error/**",
                        // "Need Help?" widget's two read-only endpoints (HelpAssistantController) -
                        // public so logged-out visitors can use the widget too.
                        "/help/**"
                ).permitAll()
                .requestMatchers("/admin/**").hasRole("SYSTEM_ADMIN")
                // Transaction log / refund / fail / archive (ManagerPaymentController):
                // MANAGER and SYSTEM_ADMIN only. PAYMENT_MANAGER has no access here - its scope
                // is payment-method configuration under "/payment-manager/**".
                .requestMatchers("/manager/payments/**").hasAnyRole("MANAGER", "SYSTEM_ADMIN")
                .requestMatchers("/manager/**").hasAnyRole("MANAGER", "SYSTEM_ADMIN")
                // Ticket check-in (camera scan / manual code entry): door staff, plus PAYMENT_MANAGER
                // (who also needs to verify tickets against payments) and SYSTEM_ADMIN for oversight.
                .requestMatchers("/staff/**").hasAnyRole("CINEMA_STAFF", "PAYMENT_MANAGER", "SYSTEM_ADMIN")
                // Customer Relations Executive's entire site scope - deliberately not shared with
                // SYSTEM_ADMIN, which has its own separate, full site-control panel. Feedback
                // management lives only under the CRE dashboard, no duplicate admin-side view.
                .requestMatchers("/cre/**").hasRole("CUSTOMER_RELATIONS_EXECUTIVE")
                // Promotion Manager's entire site scope - deliberately not shared with SYSTEM_ADMIN,
                // which has its own separate, full site-control panel.
                .requestMatchers("/promotion-manager/**").hasRole("PROMOTION_MANAGER")
                // Payment Manager's entire site scope - system-level payment method
                // configuration, distinct from Thathsarani's per-transaction Payment
                // module (/manager/payments, /payments). SYSTEM_ADMIN also allowed for oversight.
                .requestMatchers("/payment-manager/**").hasAnyRole("PAYMENT_MANAGER", "SYSTEM_ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .successHandler(authenticationSuccessHandler())
                .failureUrl("/auth/login?error")
                .permitAll()
            )
            // "Remember me" box on the login page: a signed cookie keeps the customer logged in
            // for 14 days. Each auto-login re-loads the account, so a SUSPENDED user is refused,
            // and changing the password invalidates the cookie (it is signed with the hash).
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds(14 * 24 * 60 * 60)
                .userDetailsService(userDetailsService)
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/?loggedOut")
                .deleteCookies("remember-me")
                .permitAll()
            )
            .exceptionHandling(handling -> handling
                .accessDeniedPage("/error/403")
            );

        return http.build();
    }
}
