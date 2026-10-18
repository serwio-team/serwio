package serwio.account.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
}
