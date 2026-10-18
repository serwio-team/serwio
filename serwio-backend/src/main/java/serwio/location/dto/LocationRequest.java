package serwio.location.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import serwio.validation.ValidTimeZone;

public record LocationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 500) String addressLine,
        @NotBlank @Size(max = 150) String city,
        @NotBlank @Size(max = 32) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}",
                message = "Kod kraju musi zawierać dwie wielkie litery, np. PL") String country,
        @NotBlank @Size(max = 100) @ValidTimeZone String timezone
) {}
