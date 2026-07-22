package com.example.productassistant.auth;

import com.example.productassistant.api.ApiResponse;
import com.example.productassistant.observability.RequestIdFilter;
import com.example.productassistant.security.AuthenticationRateLimiter;
import com.example.productassistant.user.AppUserEntity;
import com.example.productassistant.user.AppUserService;
import com.example.productassistant.user.AuthenticatedUser;
import com.example.productassistant.user.InvalidRegistrationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AppUserService userService;
    private final AuthenticationManager authenticationManager;
    private final AuthenticationRateLimiter rateLimiter;
    private final SecurityContextRepository securityContextRepository;
    private final CookieCsrfTokenRepository csrfTokenRepository;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthController(
            AppUserService userService,
            AuthenticationManager authenticationManager,
            AuthenticationRateLimiter rateLimiter,
            SecurityContextRepository securityContextRepository,
            CookieCsrfTokenRepository csrfTokenRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.rateLimiter = rateLimiter;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/session")
    public ApiResponse<UserSessionView> session(Authentication authentication, CsrfToken csrfToken) {
        csrfToken.getToken();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return ApiResponse.success(UserSessionView.anonymous(), requestId());
        }
        AppUserEntity user = userService.findRequired(principal.getId());
        return ApiResponse.success(toSessionView(user), requestId());
    }

    @PostMapping("/register")
    public ApiResponse<UserSessionView> register(
            @RequestBody RegisterRequest registration,
            HttpServletRequest request,
            HttpServletResponse response) {
        String emailForLimit = registration == null ? null : registration.getEmail();
        rateLimiter.check("register", request.getRemoteAddr(), emailForLimit);
        AppUserEntity user = userService.register(
                registration == null ? null : registration.getEmail(),
                registration == null ? null : registration.getPassword());
        AuthenticatedUser principal = AuthenticatedUser.from(user);
        principal.eraseCredentials();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        establishSession(authentication, request, response);
        rateLimiter.reset("register", request.getRemoteAddr(), user.getEmail());
        return ApiResponse.success(toSessionView(user), requestId());
    }

    @PostMapping("/login")
    public ApiResponse<UserSessionView> login(
            @RequestBody LoginRequest login,
            HttpServletRequest request,
            HttpServletResponse response) {
        String submittedEmail = login == null ? null : login.getEmail();
        rateLimiter.check("login", request.getRemoteAddr(), submittedEmail);

        String normalizedEmail;
        try {
            normalizedEmail = AppUserService.normalizeEmail(submittedEmail);
            AppUserService.validatePassword(login == null ? null : login.getPassword());
        } catch (InvalidRegistrationException exception) {
            throw new InvalidCredentialsException();
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            normalizedEmail, login == null ? null : login.getPassword()));
        } catch (AuthenticationException exception) {
            throw new InvalidCredentialsException();
        }

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        principal.eraseCredentials();
        AppUserEntity user = userService.findRequired(principal.getId());
        establishSession(authentication, request, response);
        rateLimiter.reset("login", request.getRemoteAddr(), normalizedEmail);
        return ApiResponse.success(toSessionView(user), requestId());
    }

    @PostMapping("/logout")
    public ApiResponse<UserSessionView> logout(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        logoutHandler.logout(request, response, authentication);
        csrfTokenRepository.saveToken(null, request, response);
        SecurityContextHolder.clearContext();
        return ApiResponse.success(UserSessionView.anonymous(), requestId());
    }

    private void establishSession(
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (request.getSession(false) == null) {
            request.getSession(true);
        } else {
            request.changeSessionId();
        }
        csrfTokenRepository.saveToken(null, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    private UserSessionView toSessionView(AppUserEntity user) {
        return UserSessionView.authenticated(user.getId(), user.getEmail(), user.getPoints());
    }

    private String requestId() {
        return MDC.get(RequestIdFilter.MDC_KEY);
    }
}
