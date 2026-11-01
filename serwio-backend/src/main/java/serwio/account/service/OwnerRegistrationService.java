package serwio.account.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.dto.OwnerRegistrationRequest;
import serwio.account.dto.UserAccountResponse;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ConflictException;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;

@Service
@RequiredArgsConstructor
public class OwnerRegistrationService {
    private final UserAccountRepository accounts;
    private final AssetOwnerRepository owners;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserAccountResponse register(OwnerRegistrationRequest request) {
        if (accounts.findByNormalizedEmail(request.email()).isPresent()) {
            throw duplicateEmail();
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var account = new UserAccount();
        account.setEmail(request.email());
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("ASSET_OWNER");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMostSpecificCause() instanceof java.sql.SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                throw duplicateEmail();
            }
            throw exception;
        }
        var owner = new AssetOwner();
        owner.setUser(account);
        owner.setName(request.firstName() + " " + request.lastName());
        owner.setCreatedAt(now);
        owners.saveAndFlush(owner);
        return new UserAccountResponse(account.getId(), account.getEmail(), account.getRole(),
                account.getStatus(), false, account.getCreatedAt());
    }

    private ConflictException duplicateEmail() {
        return new ConflictException("Konto z tym adresem e-mail już istnieje.");
    }
}
