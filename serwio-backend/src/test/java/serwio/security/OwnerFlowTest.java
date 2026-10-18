package serwio.security;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.asset.model.ServiceObject;
import serwio.asset.repository.ServiceObjectRepository;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.temporary=true", "app.demo.seed.enabled=true",
        "app.demo.seed.password=integration-test-password"})
@AutoConfigureMockMvc
class OwnerFlowTest {
    private static final String PASSWORD = "integration-test-password";
    @Autowired MockMvc mvc;
    @Autowired UserAccountRepository accounts;
    @Autowired AssetOwnerRepository owners;
    @Autowired LocationRepository locations;
    @Autowired ServiceObjectRepository objects;
    @Autowired PasswordEncoder encoder;
    @Autowired CommandLineRunner seedDemoLocation;

    private UserAccount accountA;
    private UserAccount accountB;
    private AssetOwner ownerA;
    private Location locationA;
    private Location locationB;
    private ServiceObject deviceA;
    private ServiceObject deviceB;

    @BeforeEach
    void prepareOwners() {
        accountA = account("ACTIVE", "ASSET_OWNER");
        accountB = account("ACTIVE", "ASSET_OWNER");
        ownerA = owner(accountA);
        locationA = location(ownerA);
        locationB = location(owner(accountB));
        deviceA = device(locationA);
        deviceB = device(locationB);
    }

    @Test
    void seededAccountLogsInAndSeedingIsIdempotent() throws Exception {
        var seeded = accounts.findByEmail("owner@example.com").orElseThrow();
        assertEquals("ACTIVE", seeded.getStatus());
        assertTrue(encoder.matches(PASSWORD, seeded.getPasswordHash()));
        assertNotEquals(PASSWORD, seeded.getPasswordHash());
        var userCount = accounts.count();
        var ownerCount = owners.count();
        var locationCount = locations.count();
        var deviceCount = objects.count();
        seedDemoLocation.run();
        assertEquals(userCount, accounts.count());
        assertEquals(ownerCount, owners.count());
        assertEquals(locationCount, locations.count());
        assertEquals(deviceCount, objects.count());
        var session = login(seeded.getEmail());
        mvc.perform(get("/api").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Zalogowano pomyślnie"));
        mvc.perform(get("/api/service-objects").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void createsLocationThenDeviceAndDisplaysBothLists() throws Exception {
        var session = login(accountA.getEmail());
        var created = mvc.perform(post("/api/locations").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assetOwnerId").value(ownerA.getId().toString())).andReturn();
        var locationId = id(created);
        assertEquals("/api/locations/" + locationId, created.getResponse().getHeader("Location"));
        var createdDevice = mvc.perform(post("/api/service-objects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(deviceBody(locationId)))
                .andExpect(status().isCreated()).andReturn();
        var deviceId = id(createdDevice);
        assertEquals("/api/service-objects/" + deviceId, createdDevice.getResponse().getHeader("Location"));
        mvc.perform(get("/api/locations").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(locationId)))
                .andExpect(jsonPath("$[0]", aMapWithSize(4)));
        mvc.perform(get("/api/service-objects").session(session).param("locationId", locationId))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(deviceId))
                .andExpect(jsonPath("$[0]", aMapWithSize(5)));
    }

    @Test
    void editsAndDeletesOwnData() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(put("/api/locations/{id}", locationA.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Biuro"));
        mvc.perform(put("/api/service-objects/{id}", deviceA.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(deviceBody(locationA.getId().toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Klima"));
        mvc.perform(delete("/api/service-objects/{id}", deviceA.getId()).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/service-objects/{id}", deviceA.getId()).session(session))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/service-objects").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        var emptyLocation = location(ownerA);
        mvc.perform(delete("/api/locations/{id}", emptyLocation.getId()).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/locations/{id}", emptyLocation.getId()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotReadEditOrDeleteAnotherOwnersResources() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(get("/api/locations/{id}", locationB.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/api/locations/{id}", locationB.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/locations/{id}", locationB.getId()).session(session).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/service-objects/{id}", deviceB.getId()).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/api/service-objects/{id}", deviceB.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(deviceBody(locationB.getId().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/service-objects/{id}", deviceB.getId()).session(session).with(csrf()))
                .andExpect(status().isNotFound());
        assertTrue(objects.findById(deviceB.getId()).orElseThrow().getActive());
        assertEquals(locationB.getName(), locations.findById(locationB.getId()).orElseThrow().getName());
    }

    @Test
    void cannotCreateOrMoveDeviceIntoAnotherOwnersLocation() throws Exception {
        var session = login(accountA.getEmail());
        var body = deviceBody(locationB.getId().toString());
        mvc.perform(post("/api/service-objects").session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(put("/api/service-objects/{id}", deviceA.getId()).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        mvc.perform(get("/api/service-objects/{id}", deviceA.getId()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.locationId").value(locationA.getId().toString()));
    }

    @Test
    void listsAreScopedToOwnerEvenWhenClientSuppliesAnotherOwnerId() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(get("/api/locations").session(session).param("assetOwnerId", locationB.getAssetOwner().getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(locationA.getId().toString()));
        mvc.perform(get("/api/service-objects").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(deviceA.getId().toString()));
        mvc.perform(get("/api/service-objects").session(session).param("locationId", locationB.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/service-objects").session(login(accountB.getEmail())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(deviceB.getId().toString()));
    }

    @Test
    void rejectsAnonymousRequestsAndInvalidatesSessionOnLogout() throws Exception {
        mvc.perform(get("/api/locations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/locations").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(locationBody()))
                .andExpect(status().isUnauthorized());
        var session = login(accountA.getEmail());
        mvc.perform(post("/logout").session(session).with(csrf())).andExpect(unauthenticated());
        assertTrue(session.isInvalid());
        mvc.perform(get("/api/service-objects")).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresLoginAgainWhenSessionNoLongerExists() throws Exception {
        var session = login(accountA.getEmail());
        session.invalidate();
        mvc.perform(get("/api/locations").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realCsrfTokenWorksAfterLoginAndMissingOrInvalidTokensAreRejected() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(post("/api/locations").session(session).contentType(MediaType.APPLICATION_JSON).content(locationBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/locations").session(session).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody())).andExpect(status().isForbidden());
        var csrfResponse = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        String token = JsonPath.read(csrfResponse.getResponse().getContentAsString(), "$.token");
        String header = JsonPath.read(csrfResponse.getResponse().getContentAsString(), "$.headerName");
        mvc.perform(post("/api/locations").session(session).header(header, token)
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody())).andExpect(status().isCreated());
    }

    @Test
    void invalidFormsReturnFieldErrors() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(post("/api/locations").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(locationBody().replace("Biuro", " ").replace("Europe/Warsaw", "Invalid/Zone")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").isArray())
                .andExpect(jsonPath("$.errors.timezone").isArray());
        mvc.perform(post("/api/service-objects").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"type\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.locationId").isArray())
                .andExpect(jsonPath("$.errors.name").isArray());
        mvc.perform(get("/api/locations").session(session).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/service-objects").session(session).param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsWrongPasswordInactiveAccountAndWrongRole() throws Exception {
        mvc.perform(formLogin().user(accountA.getEmail()).password("wrong"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        accountA.setStatus("INACTIVE");
        accounts.saveAndFlush(accountA);
        mvc.perform(formLogin().user(accountA.getEmail()).password(PASSWORD)).andExpect(unauthenticated());
        accountB.setRole("TECHNICIAN");
        accounts.saveAndFlush(accountB);
        mvc.perform(get("/api/locations").session(login(accountB.getEmail()))).andExpect(status().isForbidden());
    }

    @Test
    void ownerRoleWithoutOwnerProfileCannotCreateLocation() throws Exception {
        var withoutProfile = account("ACTIVE", "ASSET_OWNER");
        mvc.perform(post("/api/locations").session(login(withoutProfile.getEmail())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(locationBody())).andExpect(status().isForbidden());
    }

    @Test
    void deletingLocationWithDevicesReturnsConflictWithoutDeletingData() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(delete("/api/locations/{id}", locationA.getId()).session(session).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/locations/{id}", locationA.getId()).session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/service-objects/{id}", deviceA.getId()).session(session)).andExpect(status().isOk());
    }

    @Test
    void listsReturnTwentyItemsByDefaultAndNextPageRemainsAvailable() throws Exception {
        for (int i = 0; i < 20; i++) {
            device(location(ownerA));
        }
        var session = login(accountA.getEmail());
        for (var endpoint : new String[]{"/api/locations", "/api/service-objects"}) {
            mvc.perform(get(endpoint).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(20)));
            mvc.perform(get(endpoint).session(session).param("page", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Test
    void currentProfilesReturnOnlyOwnPublicFields() throws Exception {
        var session = login(accountA.getEmail());
        mvc.perform(get("/api/users/me").session(session).param("userId", accountB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.id").value(accountA.getId().toString()))
                .andExpect(jsonPath("$.email").value(accountA.getEmail()))
                .andExpect(jsonPath("$.role").value("ASSET_OWNER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/owners/me").session(session).param("userId", accountB.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.id").value(ownerA.getId().toString()))
                .andExpect(jsonPath("$.userId").value(accountA.getId().toString()))
                .andExpect(jsonPath("$.name").value(ownerA.getName()))
                .andExpect(jsonPath("$.createdAt").isString());
        var otherSession = login(accountB.getEmail());
        mvc.perform(get("/api/users/me").session(otherSession))
                .andExpect(jsonPath("$.id").value(accountB.getId().toString()));
        mvc.perform(get("/api/owners/me").session(otherSession))
                .andExpect(jsonPath("$.userId").value(accountB.getId().toString()));
    }

    @Test
    void currentProfilesEnforceAuthenticationRoleAndMissingProfile() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/owners/me")).andExpect(status().isUnauthorized());
        var technician = account("ACTIVE", "TECHNICIAN");
        var session = login(technician.getEmail());
        mvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("TECHNICIAN"));
        mvc.perform(get("/api/owners/me").session(session)).andExpect(status().isForbidden());
        var withoutProfile = account("ACTIVE", "ASSET_OWNER");
        mvc.perform(get("/api/owners/me").session(login(withoutProfile.getEmail())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Konto nie ma profilu właściciela"));
    }

    @Test
    void jsonLoginReturnsApiStatusAndCreatesUsableSession() throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).accept(MediaType.APPLICATION_JSON)
                        .param("username", accountA.getEmail()).param("password", PASSWORD))
                .andExpect(status().isOk()).andExpect(authenticated().withUsername(accountA.getEmail()))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.message").value("Zalogowano pomyślnie"))
                .andExpect(jsonPath("$.endpoints", hasItem("GET /api/users/me")))
                .andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api").session(session))
                .andExpect(content().json(result.getResponse().getContentAsString()));
        mvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(accountA.getEmail()));
    }

    @Test
    void jsonLoginRejectsWrongUnknownAndInactiveAccountsWithSameMessage() throws Exception {
        accountB.setStatus("INACTIVE");
        accounts.saveAndFlush(accountB);
        for (var email : new String[]{accountA.getEmail(), "unknown@example.com", accountB.getEmail()}) {
            mvc.perform(post("/login").with(csrf()).accept(MediaType.APPLICATION_JSON)
                            .param("username", email)
                            .param("password", email.equals(accountA.getEmail()) ? "wrong" : PASSWORD))
                    .andExpect(status().isUnauthorized()).andExpect(unauthenticated())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(header().doesNotExist("Location"))
                    .andExpect(jsonPath("$.detail").value("Nieprawidłowy e-mail lub hasło"));
        }
    }

    @Test
    void jsonLoginStillRequiresValidCsrf() throws Exception {
        mvc.perform(post("/login").accept(MediaType.APPLICATION_JSON)
                        .param("username", accountA.getEmail()).param("password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
        mvc.perform(post("/login").with(csrf().useInvalidToken()).accept(MediaType.APPLICATION_JSON)
                        .param("username", accountA.getEmail()).param("password", PASSWORD))
                .andExpect(status().isForbidden()).andExpect(unauthenticated());
    }

    @Test
    void browserLoginKeepsRedirectsAndJsonOptInHandlesAcceptParameters() throws Exception {
        for (var accept : new String[]{"text/html", "*/*", "application/json;q=0", "invalid"}) {
            mvc.perform(post("/login").with(csrf()).header("Accept", accept)
                            .param("username", accountA.getEmail()).param("password", PASSWORD))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/api"));
            mvc.perform(post("/login").with(csrf()).header("Accept", accept)
                            .param("username", accountA.getEmail()).param("password", "wrong"))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(csrf()).header("Accept", "application/json;charset=UTF-8, */*;q=0.1")
                        .param("username", accountA.getEmail()).param("password", PASSWORD))
                .andExpect(status().isOk()).andExpect(authenticated());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }

    private MockHttpSession login(String email) throws Exception {
        var result = mvc.perform(formLogin().user(email).password(PASSWORD))
                .andExpect(authenticated().withUsername(email)).andExpect(redirectedUrl("/api")).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount account(String status, String role) {
        var account = new UserAccount();
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setRole(role);
        account.setStatus(status);
        account.setCreatedAt(OffsetDateTime.now());
        return accounts.saveAndFlush(account);
    }

    private AssetOwner owner(UserAccount account) {
        var owner = new AssetOwner();
        owner.setUser(account);
        owner.setName("Właściciel testowy");
        owner.setCreatedAt(OffsetDateTime.now());
        return owners.saveAndFlush(owner);
    }

    private Location location(AssetOwner owner) {
        var location = new Location();
        location.setAssetOwner(owner);
        location.setName("Lokalizacja " + UUID.randomUUID());
        location.setAddressLine("Testowa 1");
        location.setCity("Warszawa");
        location.setPostalCode("00-001");
        location.setCountry("PL");
        location.setTimezone("Europe/Warsaw");
        location.setCreatedAt(OffsetDateTime.now());
        return locations.saveAndFlush(location);
    }

    private ServiceObject device(Location location) {
        var device = new ServiceObject();
        device.setLocation(location);
        device.setName("Urządzenie testowe");
        device.setType("AC");
        device.setActive(true);
        device.setCreatedAt(OffsetDateTime.now());
        return objects.saveAndFlush(device);
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String locationBody() {
        return """
                {"name":"Biuro","addressLine":"Testowa 1","city":"Warszawa",
                 "postalCode":"00-001","country":"PL","timezone":"Europe/Warsaw"}
                """;
    }

    private String deviceBody(String locationId) {
        return """
                {"locationId":"%s","name":"Klima","type":"AC"}
                """.formatted(locationId);
    }
}
