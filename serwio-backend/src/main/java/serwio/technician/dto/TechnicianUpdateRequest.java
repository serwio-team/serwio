package serwio.technician.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TechnicianUpdateRequest(
        @NotBlank @Size(max = 255) String displayName,
        @NotNull UUID branchId,
        @NotNull Boolean active
) {}
