package serwio.location.dto;

import java.util.UUID;

public record LocationResponse(
        UUID id,
        UUID assetOwnerId,
        String name,
        String addressLine,
        String city,
        String postalCode,
        String country,
        String timezone
) {}
