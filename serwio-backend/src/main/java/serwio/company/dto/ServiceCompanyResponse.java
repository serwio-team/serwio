package serwio.company.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceCompanyResponse(UUID id, UUID userId, String name, OffsetDateTime createdAt) {
}
