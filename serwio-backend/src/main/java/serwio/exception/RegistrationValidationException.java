package serwio.exception;

import java.util.List;
import java.util.Map;
import lombok.Getter;

@Getter
public class RegistrationValidationException extends RuntimeException {
    private final Map<String, List<String>> errors;

    public RegistrationValidationException(Map<String, List<String>> errors) {
        super("Popraw nieprawidłowe pola żądania.");
        this.errors = errors;
    }
}
