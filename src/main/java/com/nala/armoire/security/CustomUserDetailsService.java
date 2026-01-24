package com.nala.armoire.security;

import com.nala.armoire.model.entity.User;
import com.nala.armoire.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Custom UserDetailsService for Phone-based Authentication
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Load user by phone number (username in our case)
     */
    @Override
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with phone: " + phone));

        return buildUserDetails(user);
    }

    /**
     * Load user by ID (for JWT token validation)
     */
    public UserDetails loadUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));

        return buildUserDetails(user);
    }

    /**
     * Build UserPrincipal from User entity
     */
    private UserDetails buildUserDetails(User user) {
        return UserPrincipal.create(user);
    }
}
