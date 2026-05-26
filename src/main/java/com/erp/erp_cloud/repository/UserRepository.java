package com.erp.erp_cloud.repository;
import com.erp.erp_cloud.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Finds a user by their unique email address.
     * Crucial for the upcoming authentication and JWT generation phases.
     * * @param email The user's email address.
     * @return An Optional containing the User if found, or empty otherwise.
     */
    Optional<User> findByEmail(String email);
}