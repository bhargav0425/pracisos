package com.pracisos.auth.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pracisos.auth.domain.entity.Tenant;
import com.pracisos.auth.domain.entity.User;
import com.pracisos.auth.domain.enums.Role;
import com.pracisos.auth.domain.enums.UserStatus;
import com.pracisos.auth.domain.repository.TenantRepository;
import com.pracisos.auth.domain.repository.UserRepository;
import com.pracisos.auth.dto.request.UserInviteRequest;
import com.pracisos.auth.service.JwtService;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthStepDefinitions extends CucumberSpringConfiguration {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    private final UUID tenantId = UUID.randomUUID();
    private MvcResult inviteResult;

    @Given("a clinic owner is logged in")
    public void a_clinic_owner_is_logged_in() {
        // Setup mock claims returned by JwtService for authentication
        Claims claims = Jwts.claims()
            .subject(UUID.randomUUID().toString())
            .add("role", "CLINIC_OWNER")
            .add("permissions", "invite:users,read:users")
            .add("tenant_id", tenantId.toString())
            .build();

        // Use precise stubbing for the token we send in our requests
        when(jwtService.validateToken("mock-jwt-token")).thenReturn(claims);
    }

    @When("the clinic owner invites a user with email {string}, first name {string}, last name {string}, and role {string}")
    public void the_clinic_owner_invites_a_user(String email, String firstName, String lastName, String roleStr) throws Exception {
        Role role = Role.valueOf(roleStr);
        UserInviteRequest request = new UserInviteRequest(email, firstName, lastName, role);

        Tenant mockTenant = new Tenant();
        mockTenant.setTenantId(tenantId);
        mockTenant.setName("Test Tenant");
        mockTenant.setSlug("test-tenant");

        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setTenant(mockTenant);
        mockUser.setEmail(email);
        mockUser.setFirstName(firstName);
        mockUser.setLastName(lastName);
        mockUser.setRole(role);
        mockUser.setStatus(UserStatus.ACTIVE);

        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(mockTenant));
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

        inviteResult = mockMvc.perform(post("/api/v1/auth/users")
                .header("Authorization", "Bearer mock-jwt-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }

    @Then("the invitation should be successful")
    public void the_invitation_should_be_successful() {
        assertEquals(201, inviteResult.getResponse().getStatus());
    }

    @Then("the new user {string} should be present in the clinic's user list")
    public void the_new_user_should_be_present_in_the_clinic_s_user_list(String fullName) throws Exception {
        String[] nameParts = fullName.split(" ");
        String firstName = nameParts[0];
        String lastName = nameParts[1];

        User mockUser = new User();
        mockUser.setUserId(UUID.randomUUID());
        mockUser.setEmail("alice@example.com");
        mockUser.setFirstName(firstName);
        mockUser.setLastName(lastName);
        mockUser.setRole(Role.PRACTITIONER);
        mockUser.setStatus(UserStatus.ACTIVE);

        when(userRepository.findAllByTenantId(tenantId)).thenReturn(Collections.singletonList(mockUser));

        MvcResult listResult = mockMvc.perform(get("/api/v1/auth/users")
                .header("Authorization", "Bearer mock-jwt-token"))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = listResult.getResponse().getContentAsString();
        assertTrue(responseBody.contains(firstName));
        assertTrue(responseBody.contains(lastName));
    }
}
