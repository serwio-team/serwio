package serwio.exception;

import java.util.List;
import java.util.Map;

/** Błąd walidacji powiązanych danych; mapowany na 400 z polem {@code errors}. */
public class FieldValidationException extends RegistrationValidationException {
    public FieldValidationException(Map<String, List<String>> errors) {
        super(errors);
    }
}
