package serwio.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneId;
import java.util.Set;

public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {
    private static final Set<String> ZONES = ZoneId.getAvailableZoneIds();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Requiredness is handled by NotBlank.
        return value == null || ZONES.contains(value);
    }
}
