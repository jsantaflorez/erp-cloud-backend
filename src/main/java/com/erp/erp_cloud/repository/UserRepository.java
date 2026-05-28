package com.erp.erp_cloud.repository;

import com.erp.erp_cloud.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<String> findByEmail(String email);

    /**
     * Prevents N+1 query problems by eagerly fetching user roles and associated roles in a single database round-trip.
     */
    @Query("SELECT u FROM User u " +
            "LEFT JOIN FETCH u.userRoles ur " +
            "LEFT JOIN FETCH ur.role r " +
            "WHERE u.email = :email")
    Optional<User> findByEmailWithRoles(@Param("email") String email);

    /**
     * Extracts only the string codes of the permissions assigned to the user within a specific company context.
     */
    @Query("SELECT p.code FROM UserRole ur " +
            "JOIN ur.role r " +
            "JOIN r.permissions p " +
            "WHERE ur.user.email = :email AND ur.company.id = :companyId")
    Set<String> findPermissionCodesByEmailAndCompanyId(@Param("email") String email, @Param("companyId") Long companyId);
}