package serwio.security;

import java.util.List;
import java.util.UUID;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import serwio.account.model.UserAccount;

@Getter
public class AccountPrincipal extends User {
    private final UUID userId;

    public AccountPrincipal(UserAccount account) {
        super(account.getEmail(), account.getPasswordHash(), "ACTIVE".equals(account.getStatus()),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole())));
        this.userId = account.getId();
    }
}
