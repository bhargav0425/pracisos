package com.pracisos.booking.unit;

import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.response.PractitionerResponse;
import com.pracisos.booking.service.PractitionerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PractitionerServiceTest {

    @Mock
    private PractitionerRepository practitionerRepository;

    @InjectMocks
    private PractitionerService practitionerService;

    private UUID tenantId;
    private UUID practitionerId;
    private Practitioner practitioner;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        practitionerId = UUID.randomUUID();
        practitioner = Practitioner.builder()
            .practitionerId(practitionerId)
            .tenantId(tenantId)
            .firstName("Jane")
            .lastName("Doe")
            .email("jane.doe@clinic.com")
            .status("ACTIVE")
            .build();
    }

    @Test
    void getPractitionersByTenant_Success() {
        when(practitionerRepository.findAllByTenantIdAndStatus(tenantId, "ACTIVE"))
            .thenReturn(Arrays.asList(practitioner));

        List<PractitionerResponse> result = practitionerService.getPractitionersByTenant(tenantId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(practitionerId, result.get(0).practitionerId());
        assertEquals("Jane Doe", result.get(0).fullName());
    }

    @Test
    void getPractitioner_Success() {
        when(practitionerRepository.findByPractitionerIdAndTenantId(practitionerId, tenantId))
            .thenReturn(Optional.of(practitioner));

        PractitionerResponse result = practitionerService.getPractitioner(tenantId, practitionerId);

        assertNotNull(result);
        assertEquals(practitionerId, result.practitionerId());
        assertEquals("Jane Doe", result.fullName());
    }

    @Test
    void getPractitioner_NotFound_ThrowsException() {
        when(practitionerRepository.findByPractitionerIdAndTenantId(practitionerId, tenantId))
            .thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> practitionerService.getPractitioner(tenantId, practitionerId));
    }
}
