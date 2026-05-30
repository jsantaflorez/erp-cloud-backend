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

    Optional<User> findByEmail(String email);

    /**
     * Single database round-trip: fetches User + UserRoles + Roles + Permissions.
     * Eliminates N+1 and the need for a separate permission query at login time.
     */
    @Query("SELECT DISTINCT u FROM User u " +
            "LEFT JOIN FETCH u.userRoles ur " +
            "LEFT JOIN FETCH ur.role r " +
            "LEFT JOIN FETCH r.permissions p " +
            "WHERE u.email = :email")
    Optional<User> findByEmailWithRolesAndPermissions(@Param("email") String email);

    /**
     * Fallback option: kept as a lightweight alternative if the full fetch
     * proves too heavy for users with many roles and permissions.
     * @deprecated Prefer findByEmailWithRolesAndPermissions for the login flow.
     */
    @Deprecated
    @Query("SELECT p.code FROM UserRole ur " +
            "JOIN ur.role r " +
            "JOIN r.permissions p " +
            "WHERE ur.user.email = :email AND ur.company.id = :companyId")
    Set<String> findPermissionCodesByEmailAndCompanyId(@Param("email") String email,
                                                       @Param("companyId") Long companyId);
}