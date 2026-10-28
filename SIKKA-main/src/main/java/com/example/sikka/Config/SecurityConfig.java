package com.example.sikka.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/account/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/google-calendar/oauth/callback").permitAll()
                        .requestMatchers("/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                                "/api/v1/auth/verify-phone", "/api/v1/auth/resend-phone-verification",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/ride/get",
                                "/api/v1/ride/catalog",
                                "/api/v1/ride/get/{rideId}",
                                "/api/v1/ride/available",
                                "/api/v1/ride/wait-times",
                                "/api/v1/ride/filter"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/place/by-kind").permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/map/get/rides",
                                "/api/v1/map/get/places",
                                "/api/v1/map/get/data",
                                "/api/v1/map/get/walking-context",
                                "/api/v1/map/route/{fromType}/{fromId}/{toType}/{toId}"
                        ).permitAll()

                        .requestMatchers(HttpMethod.GET,
                                "/park-map.html",
                                "/park-map.js",
                                "/maps/park-map.jpg",
                                "/pay-test.html"
                        ).permitAll()

                        .requestMatchers(
                                "/api/v1/ride/add",
                                "/api/v1/ride/update/**",
                                "/api/v1/ride/delete/**",
                                "/api/v1/ride/admin/**",
                                "/api/v1/map/link/**",
                                "/api/v1/map/import/**"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/account/get",
                                "/api/v1/account/add",
                                "/api/v1/account/update/**",
                                "/api/v1/account/delete/**"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/visitor-profile/get",
                                "/api/v1/visitor-profile/add",
                                "/api/v1/visitor-profile/update/**",
                                "/api/v1/visitor-profile/delete/**"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/visit/get",
                                "/api/v1/visit/add",
                                "/api/v1/visit/update/**"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/visit-group/get",
                                "/api/v1/visit-group/add",
                                "/api/v1/visit-group/update/**",
                                "/api/v1/visit-group/delete/**"
                        ).hasAuthority("ADMIN")

                        .requestMatchers(
                                "/api/v1/visit-member/get",
                                "/api/v1/visit-member/add",
                                "/api/v1/visit-member/delete/**"
                        ).hasAuthority("ADMIN")
                        .requestMatchers(
                                "/api/v1/place/get",
                                "/api/v1/place/add",
                                "/api/v1/place/update/**",
                                "/api/v1/place/delete/**"
                        ).hasAuthority("ADMIN")

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login").successHandler((request, response, authentication) -> { response.setStatus(200); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"Login successful\"}"); }).failureHandler((request, response, exception) -> { response.setStatus(401); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"Invalid email or password\"}"); }).permitAll())

                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> { response.setStatus(200); response.setContentType("application/json;charset=UTF-8"); response.getWriter().write("{\"message\":\"Logged out\"}"); })
                )

                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Login required\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"message\":\"Access denied\"}");
                        })
                )

                .httpBasic(basic -> {})

                .build();
    }
}