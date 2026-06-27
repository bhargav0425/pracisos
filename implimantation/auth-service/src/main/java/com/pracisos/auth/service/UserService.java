package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.dto.request.UserUpdateRequest;
import com.pracisos.auth.dto.response.UserResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.payload.UserCreatedPayload;
import com.pracisos.auth.event.payload.UserDeactivatedPayload;
import com.pracisos.auth.event.payload.UserUpdatedPayload;
import com.pracisos.auth.exception.TenantNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    public UserResponse inviteUser(UUID tenantId, UserInviteRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new TenantNotFoundException("Tenant not found"));

        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already registered");
        }

        String tempPassword = UUID.randomUUID().toString().substring(0, 12);
        String passwordHash = passwordEncoder.encode(tempPassword);

        User user = new User(tenant, request.email(), passwordHash,
            request.firstName(), request.lastName(), request.role());
        user.setStatus(UserStatus.INVITED);
        user = userRepository.save(user);

        eventPublisher.publishUserCreated(new UserCreatedPayload(
            user.getUserId(), tenantId, user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole().name(), user.getCreatedAt()
        ));

        log.info("Invited user {} with role {} for tenant {}",
            user.getEmail(), user.getRole(), tenantId);

        return mapToResponse(user);
    }

    public UserResponse updateUser(UUID tenantId, UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setEmail(request.email());
        if (request.status() != null) {
            user.setStatus(request.status());
        }

        user = userRepository.save(user);

        eventPublisher.publishUserUpdated(new UserUpdatedPayload(
            user.getUserId(), tenantId, user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole().name(), user.getStatus().name(),
            user.getUpdatedAt()
        ));

        log.info("Updated user {} for tenant {}", userId, tenantId);

        return mapToResponse(user);
    }

    public void deactivateUser(UUID tenantId, UUID userId, String reason) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        eventPublisher.publishUserDeactivated(new UserDeactivatedPayload(
            userId, tenantId, reason, Instant.now()
        ));

        log.info("Deactivated user {} for tenant {}", userId, tenantId);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId)
            .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }
        return mapToResponse(user);
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
            user.getUserId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getFullName(), user.getRole().name(),
            user.getStatus().name(), user.getTenantId(), user.getCreatedAt()
        );
    }
}
