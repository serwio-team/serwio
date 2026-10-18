package serwio.account.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    public UserAccountResponse getCurrent() {
        var account = accounts.findById(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono konta użytkownika"));
        return new UserAccountResponse(account.getId(), account.getEmail(), account.getRole(),
                account.getStatus(), account.getCreatedAt());
    }
}
