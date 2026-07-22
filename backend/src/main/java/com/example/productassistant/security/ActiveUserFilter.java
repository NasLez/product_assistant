package com.example.productassistant.security;

import java.io.IOException;

import com.example.productassistant.user.AppUserService;
import com.example.productassistant.user.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Revalidates the database-backed enabled flag for stateful sessions.
 */
@Component
public class ActiveUserFilter extends OncePerRequestFilter {

    private final AppUserService userService;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;

    public ActiveUserFilter(
            AppUserService userService,
            JsonAuthenticationEntryPoint authenticationEntryPoint) {
        this.userService = userService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser principal
                && !userService.isActive(principal.getId())) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InsufficientAuthenticationException("User session is no longer active"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
