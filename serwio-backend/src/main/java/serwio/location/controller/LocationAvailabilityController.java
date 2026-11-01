package serwio.location.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import serwio.location.dto.AvailabilityRequest;
import serwio.location.dto.AvailabilityResponse;
import serwio.location.service.LocationAvailabilityService;

@RestController
@RequestMapping("/api/locations/{locationId}/availability")
@RequiredArgsConstructor
public class LocationAvailabilityController {
    private final LocationAvailabilityService service;

    @GetMapping
    public AvailabilityResponse get(@PathVariable UUID locationId) {
        return service.get(locationId);
    }

    @PutMapping
    public AvailabilityResponse replace(@PathVariable UUID locationId, @Valid @RequestBody AvailabilityRequest request) {
        return service.replace(locationId, request);
    }
}
