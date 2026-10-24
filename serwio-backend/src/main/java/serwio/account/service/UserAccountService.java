package serwio.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import serwio.account.dto.PasswordChangeRequest;
import serwio.account.dto.UserAccountResponse;
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAccountService {
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;

    public UserAccountResponse getCurrent() {
        var account = accounts.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono konta użytkownika"));
        return new UserAccountResponse(account.getId(), account.getEmail(), account.getRole(),
                account.getStatus(), account.getCreatedAt());
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        var account = accounts.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono konta użytkownika"));
        if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aktualne hasło jest nieprawidłowe.");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }
}
