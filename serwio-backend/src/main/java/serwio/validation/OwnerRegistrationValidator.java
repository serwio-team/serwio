package serwio.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import serwio.account.dto.OwnerRegistrationRequest;

public class OwnerRegistrationValidator implements ConstraintValidator<ValidOwnerRegistration, OwnerRegistrationRequest> {
    @Override
    public boolean isValid(OwnerRegistrationRequest request, ConstraintValidatorContext context) {
        if (request == null || request.password() == null) return true;
        context.disableDefaultConstraintViolation();
        boolean valid = true;
        if (!request.password().equals(request.confirmPassword())) {
            context.buildConstraintViolationWithTemplate("Hasła muszą być identyczne.")
                    .addPropertyNode("confirmPassword").addConstraintViolation();
            valid = false;
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            context.buildConstraintViolationWithTemplate("Hasło może zajmować najwyżej 72 bajty UTF-8.")
                    .addPropertyNode("password").addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
