package com.pracisos.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantCreateRequest(
    @NotBlank @Size(min = 3, max = 255) 
    @Pattern(regexp = "^[a-z0-9-]+$") String slug,
    @NotBlank @Size(max = 255) String name,

    @NotBlank @Email String ownerEmail,
    @NotBlank @Size(min = 6) String ownerPassword,
    @NotBlank String ownerFirstName,
    @NotBlank String ownerLastName
) {}
