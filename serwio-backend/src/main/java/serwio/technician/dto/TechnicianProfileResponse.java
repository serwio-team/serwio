package serwio.technician.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TechnicianProfileResponse(
        UUID id,
        UUID userId,
        String email,
        String displayName,
        UUID branchId,
        String branchName,
        UUID companyId,
        String companyName,
        OffsetDateTime createdAt
) {}
