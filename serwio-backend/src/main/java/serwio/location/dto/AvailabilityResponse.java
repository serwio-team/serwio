package serwio.location.dto;

import java.util.List;
import java.util.UUID;

public record AvailabilityResponse(UUID locationId, String timezone, List<AvailabilityRequest.Day> days) {}
