package com.pracisos.auth.service;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.dto.response.UserResponse;
import com.pracisos.auth.event.EventPublisher;
import com.pracisos.auth.event.UserCreatedEvent;
import com.pracisos.auth.exception.TenantNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final EventPublisher eventPublisher;

    public UserService(UserRepository userRepository, TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder, EventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
    }

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

        eventPublisher.publishUserCreated(new UserCreatedEvent(
            user.getUserId(), tenantId, user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole().name(), user.getCreatedAt()
        ));

        return mapToResponse(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(UUID tenantId) {
        return userRepository.findAllByTenantId(tenantId)
            .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private UserResponse mapToResponse(User user) {
        return new UserResponse(
            user.getUserId(), user.getEmail(), user.getFirstName(),
            user.getLastName(), user.getFullName(), user.getRole().name(),
            user.getStatus().name(), user.getTenantId(), user.getCreatedAt()
        );
    }
}
