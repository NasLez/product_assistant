package com.example.productassistant.user;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserMapper userMapper;

    public DatabaseUserDetailsService(AppUserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalizedEmail;
        try {
            normalizedEmail = AppUserService.normalizeEmail(email);
        } catch (InvalidRegistrationException exception) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        AppUserEntity user = userMapper.findByEmail(normalizedEmail);
        if (user == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return AuthenticatedUser.from(user);
    }
}
