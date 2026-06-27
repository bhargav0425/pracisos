package com.pracisos.booking.service;

import com.pracisos.booking.domain.entity.AvailabilityTemplate;
import com.pracisos.booking.domain.entity.TimeSlot;
import com.pracisos.booking.domain.repository.AvailabilityTemplateRepository;
import com.pracisos.booking.domain.repository.PractitionerRepository;
import com.pracisos.booking.dto.request.AvailabilityCreateRequest;
import com.pracisos.booking.event.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AvailabilityService {

    private final AvailabilityTemplateRepository templateRepository;
    private final PractitionerRepository practitionerRepository;
    private final TimeSlotService timeSlotService;
    private final EventPublisher eventPublisher;

    public AvailabilityTemplate createTemplate(UUID tenantId, AvailabilityCreateRequest request) {
        if (!practitionerRepository.existsByPractitionerIdAndTenantId(request.practitionerId(), tenantId)) {
            throw new RuntimeException("Practitioner not found in tenant");
        }

        AvailabilityTemplate template = AvailabilityTemplate.builder()
            .tenantId(tenantId)
            .practitionerId(request.practitionerId())
            .dayOfWeek(request.dayOfWeek())
            .startTime(request.startTime())
            .endTime(request.endTime())
            .slotDurationMinutes(request.slotDurationMinutes())
            .isActive(true)
            .build();

        template = templateRepository.save(template);
        log.info("Created availability template {} for practitioner {}",
            template.getTemplateId(), request.practitionerId());

        // Generate slots for next 30 days
        generateSlotsFromTemplate(template);
        eventPublisher.publishAvailabilityUpdated(tenantId, request.practitionerId());

        return template;
    }

    public void generateSlotsFromTemplate(AvailabilityTemplate template) {
        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<TimeSlot> slots = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            LocalDate date = today.plusDays(i);
            // DayOfWeek is 1 (Monday) to 7 (Sunday) in Java's LocalDate,
            // while the template uses 0 (Sunday) to 6 (Saturday).
            // Let's map it correctly:
            int javaDayOfWeek = date.getDayOfWeek().getValue(); // 1 = Mon, ..., 7 = Sun
            int templateDayOfWeek = javaDayOfWeek % 7;          // 1 = Mon, ..., 6 = Sat, 0 = Sun
            if (templateDayOfWeek != template.getDayOfWeek()) continue;

            Instant dayStart = date.atTime(template.getStartTime()).atZone(ZoneId.of("UTC")).toInstant();
            Instant dayEnd = date.atTime(template.getEndTime()).atZone(ZoneId.of("UTC")).toInstant();
            long durationMs = template.getSlotDurationMinutes() * 60L * 1000L;

            for (Instant slotStart = dayStart; slotStart.isBefore(dayEnd); slotStart = slotStart.plusMillis(durationMs)) {
                Instant slotEnd = slotStart.plusMillis(durationMs);
                if (slotEnd.isAfter(dayEnd)) break;

                slots.add(TimeSlot.builder()
                    .tenantId(template.getTenantId())
                    .practitionerId(template.getPractitionerId())
                    .startTime(slotStart)
                    .endTime(slotEnd)
                    .status("AVAILABLE")
                    .build());
            }
        }

        timeSlotService.saveAllSlots(slots);
        log.info("Generated {} slots for template {}", slots.size(), template.getTemplateId());
    }

    @Transactional(readOnly = true)
    public List<AvailabilityTemplate> getTemplatesByPractitioner(UUID tenantId, UUID practitionerId) {
        return templateRepository.findAllByTenantIdAndPractitionerIdAndIsActive(tenantId, practitionerId, true);
    }
}
