package serwio.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compares scalar date/time properties without traversing JPA associations. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ValidPeriod.List.class)
@Constraint(validatedBy = PeriodValidator.class)
public @interface ValidPeriod {
    String message() default "Nieprawidłowy przedział czasu";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
    String start();
    String end();
    boolean allowOpenEnd() default true;
    boolean allowBothNull() default false;
    boolean allowEqual() default false;

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface List {
        ValidPeriod[] value();
    }
}
