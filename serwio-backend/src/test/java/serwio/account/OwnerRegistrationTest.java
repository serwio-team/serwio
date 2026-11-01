package serwio.account;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import serwio.account.repository.UserAccountRepository;
import serwio.owner.repository.AssetOwnerRepository;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.temporary=true", "app.demo.seed.enabled=false"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class OwnerRegistrationTest {
    private static final String ENDPOINT = "/api/auth/register/owner";
    private static final String PASSWORD = " test-password-123 ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserAccountRepository accounts;
    @Autowired AssetOwnerRepository owners;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void createsActiveOwnerWithoutAuthenticatingAndAllowsExistingLogin() throws Exception {
        var email = email();
        var result = register(payload(" " + email.toUpperCase() + " "));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var response = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("email").asText()).isEqualTo(email);
        assertThat(response.get("role").asText()).isEqualTo("ASSET_OWNER");
        assertThat(response.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(response.has("password")).isFalse();
        assertThat(response.has("passwordHash")).isFalse();
        assertThat(response.has("token")).isFalse();
        var account = accounts.findByEmail(email).orElseThrow();
        assertThat(encoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(account.getPasswordHash()).doesNotContain(PASSWORD);
        assertThat(account.getPasswordChangeRequired()).isFalse();
        var owner = owners.findByUserId(account.getId()).orElseThrow();
        assertThat(owner.getName()).isEqualTo("Jan Kowalski");
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        var session = login(email.toUpperCase());
        mvc.perform(get("/api/users/me").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ASSET_OWNER"));
        mvc.perform(get("/api/locations").session(session)).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        for (var path : new String[]{"/api/companies/me", "/api/branches", "/api/technicians", "/api/technicians/me"}) {
            mvc.perform(get(path).session(session)).andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"firstName", "lastName", "email", "password", "confirmPassword"})
    void rejectsBlankFields(String field) throws Exception {
        var data = payload(email());
        data.put(field, "   ");
        var result = register(data);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("errors").has(field)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "long", "unicode", "mismatch", "email", "firstName", "lastName"})
    void rejectsInvalidFields(String scenario) throws Exception {
        var data = payload(email());
        var field = "password";
        switch (scenario) {
            case "short" -> data.put("password", "1234567");
            case "long" -> data.put("password", "a".repeat(73));
            case "unicode" -> data.put("password", "ą".repeat(37));
            case "mismatch" -> { field = "confirmPassword"; data.put(field, "different"); }
            case "email" -> { field = "email"; data.put(field, "invalid"); }
            default -> { field = scenario; data.put(field, "a".repeat(128)); }
        }
        var result = register(data);
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(mapper.readTree(result.getResponse().getContentAsString()).get("errors").has(field)).isTrue();
    }

    @Test
    void rejectsDuplicateEmailRegardlessOfCase() throws Exception {
        var email = email();
        assertThat(register(payload(email)).getResponse().getStatus()).isEqualTo(201);
        assertThat(register(payload(" " + email.toUpperCase() + " ")).getResponse().getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("select count(*) from user_account where lower(email) = ?", Long.class, email)).isEqualTo(1);
    }

    @Test
    void validationDoesNotLogPasswords(CapturedOutput output) throws Exception {
        var data = payload(email());
        var secret = "private-password-" + UUID.randomUUID();
        data.put("password", secret);
        data.put("confirmPassword", secret + "-different");
        assertThat(register(data).getResponse().getStatus()).isEqualTo(400);
        assertThat(output.getAll()).doesNotContain(secret);
    }

    @Test
    void databaseRejectsCaseVariantOutsideRegistration() throws Exception {
        var email = email();
        register(payload(email));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into user_account (id, email, password_hash, role, status, created_at) "
                        + "select ?, upper(email), password_hash, role, status, created_at from user_account where email = ?",
                UUID.randomUUID(), email));
    }

    @Test
    void concurrentRegistrationCreatesExactlyOneAccountAndOwner() throws Exception {
        var email = email();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> attempt = () -> {
                start.await(5, TimeUnit.SECONDS);
                return register(payload(email)).getResponse().getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from user_account where email = ?", Long.class, email)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from asset_owner o join user_account u on o.user_id = u.id where u.email = ?",
                Long.class, email)).isEqualTo(1);
    }

    @Test
    void rollsBackAccountWhenOwnerCannotBeSaved() throws Exception {
        jdbc.execute("alter table asset_owner add constraint test_owner_save_failure check (name <> 'Rollback Kowalski')");
        var email = email();
        try {
            var data = payload(email);
            data.put("firstName", "Rollback");
            assertThat(register(data).getResponse().getStatus()).isEqualTo(409);
            assertThat(accounts.findByEmail(email)).isEmpty();
        } finally {
            jdbc.execute("alter table asset_owner drop constraint test_owner_save_failure");
        }
    }

    @Test
    void ignoresPrivilegeFieldsAndStillCreatesOnlyOwner() throws Exception {
        var data = payload(email());
        data.put("role", "SERVICE_COMPANY");
        data.put("status", "INACTIVE");
        data.put("ownerId", UUID.randomUUID().toString());
        var result = register(data);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        var account = accounts.findByEmail(data.get("email")).orElseThrow();
        assertThat(account.getRole()).isEqualTo("ASSET_OWNER");
        assertThat(account.getStatus()).isEqualTo("ACTIVE");
        assertThat(owners.findByUserId(account.getId())).isPresent();
    }

    @Test
    void requiresCsrfIncludingAnonymousRegistration() throws Exception {
        mvc.perform(post(ENDPOINT).contentType("application/json").content(mapper.writeValueAsString(payload(email()))))
                .andExpect(status().isForbidden());
        var tokenResult = mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andReturn();
        var token = mapper.readTree(tokenResult.getResponse().getContentAsString());
        mvc.perform(post(ENDPOINT).session((MockHttpSession) tokenResult.getRequest().getSession(false))
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType("application/json").content(mapper.writeValueAsString(payload(email()))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/locations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/locations")).andExpect(status().isForbidden());
    }

    @Test
    void isolatesLocationsAndObjectsForTwoRegisteredOwners() throws Exception {
        var firstEmail = email();
        var secondEmail = email();
        register(payload(firstEmail));
        register(payload(secondEmail));
        var first = login(firstEmail);
        var second = login(secondEmail);
        var locationData = """
                {"name":"Prywatna lokalizacja","addressLine":"Testowa 1","city":"Warszawa",
                 "postalCode":"00-001","country":"PL","timezone":"Europe/Warsaw"}
                """;
        var locationResult = mvc.perform(post("/api/locations").session(first).with(csrf())
                        .contentType("application/json").content(locationData))
                .andExpect(status().isCreated()).andReturn();
        var locationId = mapper.readTree(locationResult.getResponse().getContentAsString()).get("id").asText();
        var objectData = mapper.writeValueAsString(Map.of("name", "Prywatny obiekt", "type", "AC", "locationId", locationId));
        var objectResult = mvc.perform(post("/api/service-objects").session(first).with(csrf())
                        .contentType("application/json").content(objectData))
                .andExpect(status().isCreated()).andReturn();
        var objectId = mapper.readTree(objectResult.getResponse().getContentAsString()).get("id").asText();
        for (var path : new String[]{"/api/locations", "/api/service-objects"}) {
            mvc.perform(get(path).session(first)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get(path).session(second)).andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        for (var entry : Map.of("/api/locations/" + locationId, locationData,
                "/api/service-objects/" + objectId, objectData).entrySet()) {
            mvc.perform(get(entry.getKey()).session(second)).andExpect(status().isNotFound());
            mvc.perform(put(entry.getKey()).session(second).with(csrf()).contentType("application/json").content(entry.getValue()))
                    .andExpect(status().isNotFound());
            mvc.perform(delete(entry.getKey()).session(second).with(csrf())).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/service-objects").session(second).with(csrf()).contentType("application/json").content(objectData))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/service-objects").session(first).contentType("application/json").content(objectData))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SERVICE_COMPANY", "TECHNICIAN"})
    void normalizedLoginRemainsCompatibleWithExistingRoles(String role) throws Exception {
        var email = email();
        register(payload(email));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var account = accounts.findByEmail(email).orElseThrow();
            account.setRole(role);
            account.setEmail(email.toUpperCase());
        });
        mvc.perform(get("/api/users/me").session(login(email))).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value(role));
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).header("Accept", "application/json")
                        .param("username", " " + email + " ").param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private MvcResult register(Map<String, String> data) throws Exception {
        return mvc.perform(post(ENDPOINT).with(csrf()).header("Accept-Language", "pl")
                .contentType("application/json").content(mapper.writeValueAsString(data))).andReturn();
    }

    private Map<String, String> payload(String email) {
        return new java.util.HashMap<>(Map.of("firstName", " Jan ", "lastName", " Kowalski ",
                "email", email, "password", PASSWORD, "confirmPassword", PASSWORD));
    }

    private String email() {
        return UUID.randomUUID() + "@example.com";
    }
}
