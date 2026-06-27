package com.pracisos.auth.unit;

import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
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
import com.pracisos.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    private UUID tenantId;
    private Tenant testTenant;
    private UserInviteRequest inviteRequest;
    private User testUser;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        testTenant = new Tenant("maple-health", "Maple Health Clinic");
        testTenant.setTenantId(tenantId);

        inviteRequest = new UserInviteRequest("dr.bob@maple-health.com", "Bob", "Jones", Role.PRACTITIONER);

        testUser = new User(testTenant, "dr.bob@maple-health.com", "hashed_pwd", "Bob", "Jones", Role.PRACTITIONER);
        testUser.setUserId(UUID.randomUUID());
        testUser.setStatus(UserStatus.INVITED);
    }

    @Test
    void inviteUser_WithValidRequest_SavesAndPublishesEvent() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        when(userRepository.existsByEmail(inviteRequest.email())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed_pwd");
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        UserResponse response = userService.inviteUser(tenantId, inviteRequest);

        assertNotNull(response);
        assertEquals(testUser.getUserId(), response.userId());
        assertEquals(inviteRequest.email(), response.email());
        assertEquals("Bob Jones", response.fullName());
        assertEquals("INVITED", response.status());
        assertEquals("PRACTITIONER", response.role());

        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishUserCreated(any(UserCreatedPayload.class));
    }

    @Test
    void inviteUser_WithNonExistentTenant_ThrowsTenantNotFoundException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        assertThrows(TenantNotFoundException.class, () -> userService.inviteUser(tenantId, inviteRequest));

        verify(userRepository, never()).save(any(User.class));
        verify(eventPublisher, never()).publishUserCreated(any(UserCreatedPayload.class));
    }

    @Test
    void inviteUser_WithDuplicateEmail_ThrowsRuntimeException() {
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(testTenant));
        when(userRepository.existsByEmail(inviteRequest.email())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> userService.inviteUser(tenantId, inviteRequest));

        verify(userRepository, never()).save(any(User.class));
        verify(eventPublisher, never()).publishUserCreated(any(UserCreatedPayload.class));
    }

    @Test
    void updateUser_Success() {
        UserUpdateRequest updateRequest = new UserUpdateRequest("Bobby", "Jones", "dr.bob@maple-health.com", UserStatus.ACTIVE);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponse response = userService.updateUser(tenantId, testUser.getUserId(), updateRequest);

        assertNotNull(response);
        assertEquals("Bobby", response.firstName());
        assertEquals("ACTIVE", response.status());
        verify(eventPublisher).publishUserUpdated(any(UserUpdatedPayload.class));
    }

    @Test
    void updateUser_CrossTenant_ThrowsException() {
        UserUpdateRequest updateRequest = new UserUpdateRequest("Bobby", "Jones", "dr.bob@maple-health.com", UserStatus.ACTIVE);
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        assertThrows(RuntimeException.class, () -> userService.updateUser(UUID.randomUUID(), testUser.getUserId(), updateRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deactivateUser_Success() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        userService.deactivateUser(tenantId, testUser.getUserId(), "No longer with clinic");

        assertEquals(UserStatus.INACTIVE, testUser.getStatus());
        verify(userRepository).save(testUser);
        verify(eventPublisher).publishUserDeactivated(any(UserDeactivatedPayload.class));
    }

    @Test
    void deactivateUser_CrossTenant_ThrowsException() {
        when(userRepository.findById(testUser.getUserId())).thenReturn(Optional.of(testUser));

        assertThrows(RuntimeException.class, () -> userService.deactivateUser(UUID.randomUUID(), testUser.getUserId(), "No longer with clinic"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void getUsersByTenant_ReturnsList() {
        when(userRepository.findAllByTenantId(tenantId)).thenReturn(Collections.singletonList(testUser));

        List<UserResponse> list = userService.getUsersByTenant(tenantId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(testUser.getEmail(), list.get(0).email());

        verify(userRepository).findAllByTenantId(tenantId);
    }
}
