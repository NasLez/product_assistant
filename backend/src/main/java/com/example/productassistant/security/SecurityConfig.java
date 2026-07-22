package com.example.productassistant.security;

import com.example.productassistant.user.DatabaseUserDetailsService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            DatabaseUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        return repository;
    }

    @Bean
    public FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration(
            ActiveUserFilter activeUserFilter) {
        FilterRegistrationBean<ActiveUserFilter> registration =
                new FilterRegistrationBean<>(activeUserFilter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            DaoAuthenticationProvider authenticationProvider,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository,
            ActiveUserFilter activeUserFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .authenticationProvider(authenticationProvider)
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/", "/index.html", "/favicon.ico", "/assets/**", "/error",
                                "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/register")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults());
        http.addFilterAfter(activeUserFilter, SecurityContextHolderFilter.class);
        return http.build();
    }
}
