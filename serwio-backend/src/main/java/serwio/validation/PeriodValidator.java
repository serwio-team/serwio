package serwio.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ValidationException;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import org.springframework.beans.PropertyAccessorFactory;

public class PeriodValidator implements ConstraintValidator<ValidPeriod, Object> {
    private ValidPeriod rule;

    @Override
    public void initialize(ValidPeriod rule) {
        this.rule = rule;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) return true;
        var properties = PropertyAccessorFactory.forBeanPropertyAccess(value);
        var start = properties.getPropertyValue(rule.start());
        var end = properties.getPropertyValue(rule.end());
        boolean valid;
        if (start == null && end == null) {
            valid = rule.allowBothNull();
        } else if (start == null) {
            valid = false;
        } else if (end == null) {
            valid = rule.allowOpenEnd();
        } else {
            int comparison;
            if (start instanceof OffsetDateTime from && end instanceof OffsetDateTime to) {
                comparison = from.toInstant().compareTo(to.toInstant());
            } else if (start instanceof LocalTime from && end instanceof LocalTime to) {
                comparison = from.compareTo(to);
            } else {
                throw new ValidationException("ValidPeriod requires matching OffsetDateTime or LocalTime properties");
            }
            valid = rule.allowEqual() ? comparison <= 0 : comparison < 0;
        }
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(start == null ? rule.start() : rule.end())
                    .addConstraintViolation();
        }
        return valid;
    }
}
