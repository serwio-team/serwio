package serwio.order.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceOrderListItemResponse(
        UUID id, UUID serviceObjectId, String serviceObjectName, UUID locationId,
        String locationName, String orderType, String currentStatus, OffsetDateTime createdAt) {}
