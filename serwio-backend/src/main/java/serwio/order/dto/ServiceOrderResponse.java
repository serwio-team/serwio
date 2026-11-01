package serwio.order.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceOrderResponse(
        UUID id, UUID serviceObjectId, String serviceObjectName,
        UUID locationId, String locationName, String addressLine, String city, String timezone,
        UUID assetOwnerId, String assetOwnerName, UUID serviceCompanyId,
        String orderType, String description, String currentStatus,
        UUID createdByUserId, OffsetDateTime createdAt, OffsetDateTime closedAt) {}
