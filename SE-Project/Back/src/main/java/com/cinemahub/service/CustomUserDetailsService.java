package com.cinemahub.service;

import com.cinemahub.model.User;
import com.cinemahub.model.UserStatus;
import com.cinemahub.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Bridges our {@link User} entity to Spring Security. Spring Security calls
 * loadUserByUsername() during login and uses the returned UserDetails to
 * check the password hash and the granted authority ("ROLE_xxx").
 * A suspended account is marked disabled, which Spring Security rejects at
 * login with "User is disabled" instead of letting them sign in.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account found for " + email));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .disabled(user.getStatus() == UserStatus.SUSPENDED)
                .build();
    }
}
