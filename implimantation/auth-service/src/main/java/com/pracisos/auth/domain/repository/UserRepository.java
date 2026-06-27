package com.pracisos.auth.domain.repository;

import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.tenant.tenantId = :tenantId")
    List<User> findAllByTenantId(@Param("tenantId") UUID tenantId);

    @Query("SELECT u FROM User u WHERE u.tenant.tenantId = :tenantId AND u.role = :role")
    List<User> findAllByTenantIdAndRole(@Param("tenantId") UUID tenantId, @Param("role") Role role);
}
