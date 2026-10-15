package serwio.asset.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record ServiceObjectResponse(
        UUID id,
        UUID locationId,
        String locationName,
        String name,
        String type,
        String description,
        String manufacturer,
        String model,
        String serialNumber,
        Map<String, Object> attributes,
        UUID publicId,
        OffsetDateTime qrGeneratedAt,
        boolean active,
        OffsetDateTime createdAt
) {}
