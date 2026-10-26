package serwio.company.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceBranchResponse(
        UUID id,
        String name,
        boolean active,
        long activeTechnicianCount,
        OffsetDateTime createdAt
) {}
