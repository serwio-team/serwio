package serwio.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

public record ServiceObjectRequest(
        @NotNull UUID locationId,
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String type,
        @Size(max = 100000) String description,
        @Size(max = 255) String manufacturer,
        @Size(max = 255) String model,
        @Size(max = 255) String serialNumber,
        @Size(max = 100) Map<@NotBlank @Size(max = 100) String, Object> attributes
) {}
