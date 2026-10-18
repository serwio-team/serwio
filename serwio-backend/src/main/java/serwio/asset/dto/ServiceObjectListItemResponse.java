package serwio.asset.dto;

import java.util.UUID;

public record ServiceObjectListItemResponse(
        UUID id,
        String name,
        String type,
        UUID locationId,
        String locationName
) {}
