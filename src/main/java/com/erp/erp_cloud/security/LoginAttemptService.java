package com.erp.erp_cloud.security;

import com.erp.erp_cloud.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Account lockout after repeated failed logins (audit 2026-11-02, A2).
 * The users.failed_login_attempts / locked_until columns already existed
 * but were never written; UserPrincipal.isAccountNonLocked() reads them.
 */
@Slf4j
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final int maxFailedAttempts;
    private final long lockMinutes;

    public LoginAttemptService(
            UserRepository userRepository,
            @Value("${app.security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.login.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockMinutes = lockMinutes;
    }

    /** Counts a failed password for this email; locks the account at the threshold. */
    @Transactional
    public void recordFailure(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
            user.setFailedLoginAttempts(attempts);
            if (attempts >= maxFailedAttempts) {
                user.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
                user.setFailedLoginAttempts(0);
                log.warn("AUTH_LOCKOUT | userId: {} | lockedMinutes: {}", user.getId(), lockMinutes);
            }
            userRepository.save(user);
        });
    }

    /** Clears the failure counter after a successful login. */
    @Transactional
    public void recordSuccess(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}
