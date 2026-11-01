package serwio.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OwnerRegistrationValidator.class)
public @interface ValidOwnerRegistration {
    String message() default "Popraw dane rejestracji.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
