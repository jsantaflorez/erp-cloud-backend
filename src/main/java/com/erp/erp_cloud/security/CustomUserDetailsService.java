package com.erp.erp_cloud.security;

import com.erp.erp_cloud.entity.User;
import com.erp.erp_cloud.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom implementation of Spring Security's core UserDetailsService.
 * Responsible for retrieving user credentials from the production database.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Locates the user based on the email string provided during authentication.
     * * @param email The identifier identifying the user requiring authentication.
     * @return A fully populated UserDetails instance required by the authentication manager.
     * @throws UsernameNotFoundException If the email is not registered in the system.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        String.format("User not found with registered email: %s", email)
                ));

        return new CustomUserDetails(user);
    }
}