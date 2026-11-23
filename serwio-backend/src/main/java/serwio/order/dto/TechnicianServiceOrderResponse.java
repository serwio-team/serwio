package serwio.order.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TechnicianServiceOrderResponse(
        UUID id, UUID serviceObjectId, String serviceObjectName,
        UUID locationId, String locationName, String addressLine, String city, String timezone,
        String orderType, String description, String currentStatus, OffsetDateTime createdAt,
        Coordinator coordinator) implements ServiceOrderDetailsResponse {

    public record Coordinator(UUID id, String displayName) {}
}
