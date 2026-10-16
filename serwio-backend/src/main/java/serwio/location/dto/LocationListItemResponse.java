package serwio.location.dto;

import java.util.UUID;

public record LocationListItemResponse(
        UUID id,
        String name,
        String addressLine,
        String city
) {}
