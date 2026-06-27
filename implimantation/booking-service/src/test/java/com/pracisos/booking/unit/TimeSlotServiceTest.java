package com.pracisos.booking.unit;

import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.response.SlotResponse;
import com.pracisos.booking.exception.SlotNotAvailableException;
import com.pracisos.booking.service.TimeSlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TimeSlotServiceTest {

    @Mock
    private TimeSlotRepository slotRepository;

    @InjectMocks
    private TimeSlotService timeSlotService;

    private UUID tenantId;
    private UUID practitionerId;
    private UUID slotId;
    private TimeSlot timeSlot;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        practitionerId = UUID.randomUUID();
        slotId = UUID.randomUUID();
        timeSlot = TimeSlot.builder()
            .slotId(slotId)
            .tenantId(tenantId)
            .practitionerId(practitionerId)
            .startTime(Instant.now().plusSeconds(3600))
            .endTime(Instant.now().plusSeconds(7200))
            .status("AVAILABLE")
            .version(1)
            .build();
    }

    @Test
    void getAvailableSlots_Success() {
        Instant from = Instant.now();
        Instant to = Instant.now().plusSeconds(86400);

        when(slotRepository.findAvailableSlots(tenantId, practitionerId, from, to))
            .thenReturn(Arrays.asList(timeSlot));

        List<SlotResponse> result = timeSlotService.getAvailableSlots(tenantId, practitionerId, from, to);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(slotId, result.get(0).slotId());
        assertEquals("AVAILABLE", result.get(0).status());
    }

    @Test
    void lockSlot_Success() {
        when(slotRepository.findByIdWithLock(slotId, tenantId)).thenReturn(Optional.of(timeSlot));
        when(slotRepository.save(any(TimeSlot.class))).thenAnswer(i -> i.getArgument(0));

        TimeSlot result = timeSlotService.lockSlot(slotId, tenantId);

        assertNotNull(result);
        assertEquals("LOCKED", result.getStatus());
        verify(slotRepository).save(timeSlot);
    }

    @Test
    void lockSlot_SlotNotFound_ThrowsException() {
        when(slotRepository.findByIdWithLock(slotId, tenantId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> timeSlotService.lockSlot(slotId, tenantId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void lockSlot_SlotNotAvailable_ThrowsException() {
        timeSlot.setStatus("BOOKED");
        when(slotRepository.findByIdWithLock(slotId, tenantId)).thenReturn(Optional.of(timeSlot));

        assertThrows(SlotNotAvailableException.class, () -> timeSlotService.lockSlot(slotId, tenantId));
        verify(slotRepository, never()).save(any());
    }

    @Test
    void unlockSlot_Success() {
        timeSlot.setStatus("LOCKED");
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(timeSlot));
        when(slotRepository.save(any(TimeSlot.class))).thenAnswer(i -> i.getArgument(0));

        timeSlotService.unlockSlot(slotId, tenantId);

        assertEquals("AVAILABLE", timeSlot.getStatus());
        verify(slotRepository).save(timeSlot);
    }

    @Test
    void unlockSlot_CrossTenant_ThrowsException() {
        UUID otherTenantId = UUID.randomUUID();
        when(slotRepository.findById(slotId)).thenReturn(Optional.of(timeSlot));

        assertThrows(RuntimeException.class, () -> timeSlotService.unlockSlot(slotId, otherTenantId));
        verify(slotRepository, never()).save(any());
    }
}
