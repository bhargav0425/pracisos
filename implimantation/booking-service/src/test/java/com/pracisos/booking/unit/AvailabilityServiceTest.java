package com.pracisos.booking.unit;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import com.pracisos.booking.domain.entity.Practitioner;
import com.pracisos.booking.domain.repository.AvailabilityTemplateRepository;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.request.AvailabilityCreateRequest;
import com.pracisos.booking.event.EventPublisher;
import com.pracisos.booking.service.AvailabilityService;
import com.pracisos.booking.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AvailabilityServiceTest {

    @Mock
    private AvailabilityTemplateRepository templateRepository;
    @Mock
    private PractitionerRepository practitionerRepository;
    @Mock
    private TimeSlotService timeSlotService;
    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private AvailabilityService availabilityService;

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
    void createTemplate_Success() {
        AvailabilityCreateRequest request = new AvailabilityCreateRequest(
            practitionerId,
            1, // Monday
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            30 // 30 mins
        );

        when(practitionerRepository.existsByPractitionerIdAndTenantId(practitionerId, tenantId)).thenReturn(true);
        when(templateRepository.save(any(AvailabilityTemplate.class))).thenAnswer(invocation -> {
            AvailabilityTemplate temp = invocation.getArgument(0);
            temp.setTemplateId(UUID.randomUUID());
            return temp;
        });

        AvailabilityTemplate result = availabilityService.createTemplate(tenantId, request);

        assertNotNull(result);
        assertEquals(practitionerId, result.getPractitionerId());
        assertEquals(tenantId, result.getTenantId());
        assertEquals(1, result.getDayOfWeek());
        assertEquals(LocalTime.of(9, 0), result.getStartTime());
        assertEquals(LocalTime.of(12, 0), result.getEndTime());
        assertEquals(30, result.getSlotDurationMinutes());
        assertTrue(result.getIsActive());

        verify(templateRepository).save(any(AvailabilityTemplate.class));
        verify(timeSlotService).saveAllSlots(anyList());
        verify(eventPublisher).publishAvailabilityUpdated(tenantId, practitionerId);
    }

    @Test
    void createTemplate_PractitionerNotFound_ThrowsException() {
        AvailabilityCreateRequest request = new AvailabilityCreateRequest(
            practitionerId,
            1,
            LocalTime.of(9, 0),
            LocalTime.of(12, 0),
            30
        );

        when(practitionerRepository.existsByPractitionerIdAndTenantId(practitionerId, tenantId)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> availabilityService.createTemplate(tenantId, request));

        verify(templateRepository, never()).save(any(AvailabilityTemplate.class));
        verify(timeSlotService, never()).saveAllSlots(anyList());
        verify(eventPublisher, never()).publishAvailabilityUpdated(any(), any());
    }

    @Test
    void generateSlotsFromTemplate_GeneratesCorrectSlots() {
        AvailabilityTemplate template = AvailabilityTemplate.builder()
            .templateId(UUID.randomUUID())
            .tenantId(tenantId)
            .practitionerId(practitionerId)
            .dayOfWeek(1) // Monday
            .startTime(LocalTime.of(9, 0))
            .endTime(LocalTime.of(11, 0)) // 2 hours
            .slotDurationMinutes(30) // should generate 4 slots per Monday
            .isActive(true)
            .build();

        availabilityService.generateSlotsFromTemplate(template);

        ArgumentCaptor<List> slotsCaptor = ArgumentCaptor.forClass(List.class);
        verify(timeSlotService).saveAllSlots(slotsCaptor.capture());

        List slots = slotsCaptor.getValue();
        assertNotNull(slots);
        // In 30 days, there are either 4 or 5 Mondays.
        // Each Monday has 4 slots (9:00, 9:30, 10:00, 10:30).
        // Total slots should be 4 * (number of Mondays in next 30 days).
        assertTrue(slots.size() >= 16 && slots.size() <= 20);
    }
}
