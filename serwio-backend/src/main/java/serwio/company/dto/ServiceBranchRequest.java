package serwio.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ServiceBranchRequest(
        @NotBlank @Size(max = 255) String name
) {}
