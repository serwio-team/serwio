package serwio.account.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import serwio.account.dto.PasswordChangeRequest;
import serwio.account.dto.UserAccountResponse;
import serwio.account.service.UserAccountService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAccountController {
    private final UserAccountService service;

    @GetMapping("/me")
    public UserAccountResponse current() {
        return service.getCurrent();
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(request);
        return ResponseEntity.noContent().build();
    }
}
