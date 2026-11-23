package serwio.security;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import serwio.account.model.UserAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {
    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlyAnAuthenticatedSerwioAccountHasACurrentUserId() {
        assertThatThrownBy(currentUser::id).isInstanceOf(AccessDeniedException.class);
        var account = new UserAccount();
        var id = UUID.randomUUID();
        account.setId(id);
        account.setEmail("technician@example.com");
        account.setPasswordHash("fixture");
        account.setRole("TECHNICIAN");
        account.setStatus("ACTIVE");
        var principal = new AccountPrincipal(account);
        var context = SecurityContextHolder.getContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated(principal, null));
        assertThatThrownBy(currentUser::id).isInstanceOf(AccessDeniedException.class);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("anonymous", null, principal.getAuthorities()));
        assertThatThrownBy(currentUser::id).isInstanceOf(AccessDeniedException.class);
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        assertThat(currentUser.id()).isEqualTo(id);
    }
}
