package serwio.account.controller;

import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import serwio.account.dto.OwnerRegistrationRequest;
import serwio.account.dto.UserAccountResponse;
import serwio.account.service.OwnerRegistrationService;
import serwio.exception.RegistrationValidationException;

@RestController
@RequiredArgsConstructor
public class OwnerRegistrationController {
    private final OwnerRegistrationService service;
    private final Validator validator;

    @PostMapping("/api/auth/register/owner")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountResponse register(@RequestBody OwnerRegistrationRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        validator.validate(request).forEach(violation ->
                errors.computeIfAbsent(violation.getPropertyPath().toString(), key -> new ArrayList<>())
                        .add(violation.getMessage()));
        if (!errors.isEmpty()) throw new RegistrationValidationException(errors);
        return service.register(request);
    }
}
