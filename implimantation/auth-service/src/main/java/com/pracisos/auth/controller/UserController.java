package com.pracisos.auth.controller;

import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.dto.request.UserUpdateRequest;
import com.pracisos.auth.dto.response.UserResponse;
import com.pracisos.auth.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<UserResponse> inviteUser(
        @RequestAttribute("tenantId") UUID tenantId,
        @Valid @RequestBody UserInviteRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(userService.inviteUser(tenantId, request));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<List<UserResponse>> getUsers(
        @RequestAttribute("tenantId") UUID tenantId
    ) {
        return ResponseEntity.ok(userService.getUsersByTenant(tenantId));
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<UserResponse> updateUser(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(tenantId, id, request));
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('CLINIC_OWNER')")
    public ResponseEntity<Void> deactivateUser(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable UUID id,
        @RequestParam(required = false, defaultValue = "Deactivated by admin") String reason
    ) {
        userService.deactivateUser(tenantId, id, reason);
        return ResponseEntity.noContent().build();
    }
}
