package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.TimeSlotRepository;
import com.pracisos.booking.dto.response.SlotResponse;
import com.pracisos.booking.exception.SlotNotAvailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class TimeSlotService {

    private final TimeSlotRepository slotRepository;

    public void saveAllSlots(List<TimeSlot> slots) {
        slotRepository.saveAll(slots);
    }

    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(UUID tenantId, UUID practitionerId, Instant from, Instant to) {
        return slotRepository.findAvailableSlots(tenantId, practitionerId, from, to)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    public TimeSlot lockSlot(UUID slotId, UUID tenantId) {
        TimeSlot slot = slotRepository.findByIdWithLock(slotId, tenantId)
            .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!"AVAILABLE".equals(slot.getStatus())) {
            throw new SlotNotAvailableException("Slot is no longer available");
        }

        slot.setStatus("LOCKED");
        return slotRepository.save(slot);
    }

    public void unlockSlot(UUID slotId, UUID tenantId) {
        TimeSlot slot = slotRepository.findById(slotId)
            .orElseThrow(() -> new RuntimeException("Slot not found"));

        if (!slot.getTenantId().equals(tenantId)) {
            throw new RuntimeException("Cross-tenant access denied");
        }

        slot.setStatus("AVAILABLE");
        slotRepository.save(slot);
    }

    private SlotResponse mapToResponse(TimeSlot slot) {
        return new SlotResponse(
            slot.getSlotId(),
            slot.getPractitionerId(),
            slot.getStartTime(),
            slot.getEndTime(),
            slot.getStatus()
        );
    }
}
