package serwio.technician.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TechnicianResponse(
        UUID id,
        UUID userId,
        String email,
        String displayName,
        UUID branchId,
        String branchName,
        boolean active,
        OffsetDateTime createdAt
) {}
