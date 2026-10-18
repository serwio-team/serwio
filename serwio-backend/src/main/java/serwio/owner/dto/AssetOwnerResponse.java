package serwio.owner.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetOwnerResponse(UUID id, UUID userId, String name, OffsetDateTime createdAt) {
}
