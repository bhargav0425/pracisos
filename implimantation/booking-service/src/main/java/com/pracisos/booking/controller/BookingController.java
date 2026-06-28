package com.pracisos.booking.controller;

import com.pracisos.booking.dto.request.BookingCreateRequest;
import com.pracisos.booking.dto.request.BookingStatusUpdateRequest;
import com.pracisos.booking.dto.response.BookingResponse;
import com.pracisos.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/appointments")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> createBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @Valid @RequestBody BookingCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(bookingService.createBooking(tenantId, request));
    }

    @GetMapping("/appointments")
    @PreAuthorize("hasAnyRole('PATIENT', 'PRACTITIONER', 'RECEPTIONIST', 'CLINIC_OWNER')")
    public ResponseEntity<List<BookingResponse>> getBookings(
        @RequestAttribute("tenantId") UUID tenantId,
        @RequestAttribute("userId") UUID userId,
        @RequestAttribute("role") String role
    ) {
        return switch (role) {
            case "PATIENT" -> ResponseEntity.ok(bookingService.getBookingsByPatient(tenantId, userId));
            case "PRACTITIONER" -> ResponseEntity.ok(bookingService.getBookingsByPractitioner(tenantId, userId));
            default -> ResponseEntity.ok(bookingService.getAllBookings(tenantId));
        };
    }

    @PutMapping("/appointments/{id}/cancel")
    @PreAuthorize("hasAnyRole('PATIENT', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> cancelBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable("id") UUID id,
        @Valid @RequestBody BookingStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(bookingService.cancelBooking(tenantId, id, request.reason()));
    }

    @PutMapping("/appointments/{id}/complete")
    @PreAuthorize("hasRole('PRACTITIONER')")
    public ResponseEntity<BookingResponse> completeBooking(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(bookingService.completeBooking(tenantId, id));
    }

    @PutMapping("/appointments/{id}/no-show")
    @PreAuthorize("hasAnyRole('PRACTITIONER', 'RECEPTIONIST')")
    public ResponseEntity<BookingResponse> markNoShow(
        @RequestAttribute("tenantId") UUID tenantId,
        @PathVariable("id") UUID id
    ) {
        return ResponseEntity.ok(bookingService.markNoShow(tenantId, id));
    }
}
