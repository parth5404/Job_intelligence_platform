package com.samyak.job_intelligence.auth.config;

import com.samyak.job_intelligence.auth.security.JwtAuthenticationFilter;
import com.samyak.job_intelligence.auth.security.JwtTokenProvider;
import com.samyak.job_intelligence.auth.security.RestAccessDeniedHandler;
import com.samyak.job_intelligence.auth.security.RestAuthenticationEntryPoint;
import com.samyak.job_intelligence.candidate.repository.CandidateProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Clock;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtTokenProvider tokenProvider,
            CandidateProfileRepository candidateProfileRepository,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler
    ) {
        // Created here, not exposed as a bean, so it is registered only in this security filter chain.
        JwtAuthenticationFilter jwtAuthenticationFilter =
                new JwtAuthenticationFilter(tokenProvider, candidateProfileRepository);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Makes Spring Boot back off from creating its default in-memory user with a generated password.
     * Authentication happens exclusively through JWT bearer tokens, so this service never resolves a user.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Username/password authentication is not supported");
        };
    }
}
