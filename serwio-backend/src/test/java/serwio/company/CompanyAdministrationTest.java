package serwio.company;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import serwio.testsupport.OrderApiFixture;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyAdministrationTest extends OrderApiFixture {
    @Test
    void managesBranchesAndRejectsForeignDuplicateOrLastActiveBranchChanges() throws Exception {
        var firm = login(companyA);
        var otherFirm = login(companyB);
        mvc.perform(get("/api/companies/me").session(firm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firmA.getId().toString()));
        mvc.perform(get("/api/companies/me/summary").session(firm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranchCount").value(2))
                .andExpect(jsonPath("$.inactiveBranchCount").value(1));
        var created = createBranch(firm, "  Nowy oddział  ").andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nowy oddział")).andReturn();
        var id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var path = "/api/branches/" + id;
        createBranch(firm, "nowy ODDZIAŁ").andExpect(status().isConflict());
        mvc.perform(get("/api/branches").session(firm)).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/branches").param("page", "99").session(firm)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(path).session(otherFirm)).andExpect(status().isNotFound());
        mvc.perform(putRequest(path, Map.of("name", "A1 Warszawa")).session(firm).with(csrf()))
                .andExpect(status().isConflict());
        mvc.perform(putRequest(path, Map.of("name", "Oddział po zmianie")).session(firm).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Oddział po zmianie"));
        mvc.perform(delete(path).session(otherFirm).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(delete("/api/branches/" + a1.getId()).session(firm).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete(path).session(firm).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete(path).session(firm).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path).session(firm)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        var newAccount = account("SERVICE_COMPANY", "ACTIVE");
        company(newAccount);
        var emptyFirm = login(newAccount);
        mvc.perform(get("/api/branches").session(emptyFirm)).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        var sole = createBranch(emptyFirm, "Jedyny oddział").andExpect(status().isCreated()).andReturn();
        var soleId = mapper.readTree(sole.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(delete("/api/branches/" + soleId).session(emptyFirm).with(csrf())).andExpect(status().isConflict());
    }

    @Test
    void provisionsTechnicianResetsPasswordAndRevokesExistingSessionOnDeactivation() throws Exception {
        var firm = login(companyA);
        var otherFirm = login(companyB);
        var email = "test-" + UUID.randomUUID() + "@example.com";
        var created = createTechnician(firm, a2.getId(), email.toUpperCase(java.util.Locale.ROOT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.accountStatus").value("PENDING")).andReturn();
        var id = mapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        var path = "/api/technicians/" + id;
        createTechnician(firm, a2.getId(), email).andExpect(status().isConflict());
        createTechnician(firm, aInactive.getId(), "unused@example.com").andExpect(status().isConflict());
        createTechnician(firm, b1.getId(), "unused@example.com").andExpect(status().isNotFound());
        mvc.perform(get(path).session(otherFirm)).andExpect(status().isNotFound());
        mvc.perform(get(path).session(firm)).andExpect(status().isOk());
        mvc.perform(get("/api/technicians").session(firm)).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/technicians").param("branchId", a2.getId().toString()).session(firm))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/companies/me/summary").session(firm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingTechnicianCount").value(1));

        var technician = loginWithPassword(email, PASSWORD);
        mvc.perform(get("/api/technicians/me").session(technician)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        var ownPassword = "/api/users/me/password";
        mvc.perform(putRequest(ownPassword, Map.of("currentPassword", "incorrect", "newPassword", "new-test-password"))
                .session(technician).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(putRequest(ownPassword, Map.of("currentPassword", PASSWORD, "newPassword", "new-test-password"))
                .session(technician).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path).session(firm)).andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        mvc.perform(putRequest(path + "/password", Map.of("password", "reset-test-password")).session(otherFirm).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(putRequest(path + "/password", Map.of("password", "reset-test-password")).session(firm).with(csrf()))
                .andExpect(status().isNoContent());
        technician = loginWithPassword(email, "reset-test-password");
        mvc.perform(get("/api/users/me").session(technician)).andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));

        var disabled = Map.<String, Object>of("displayName", "Nowe imię", "branchId", a2.getId(), "active", false);
        mvc.perform(putRequest(path, disabled).session(otherFirm).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(putRequest(path, disabled).session(firm).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
        mvc.perform(get("/api/technicians/me").session(technician)).andExpect(status().isUnauthorized());
    }

    @Test
    void companyAccountWithoutProfileAndTechnicianWithoutProfileCannotUseTheirEndpoints() throws Exception {
        var incompleteCompany = login(account("SERVICE_COMPANY", "ACTIVE"));
        var incompleteTechnician = login(account("TECHNICIAN", "ACTIVE"));
        mvc.perform(get("/api/companies/me/summary").session(incompleteCompany)).andExpect(status().isNotFound());
        createBranch(incompleteCompany, "Brak firmy").andExpect(status().isForbidden());
        createTechnician(incompleteCompany, a1.getId(), "unused@example.com").andExpect(status().isForbidden());
        mvc.perform(get("/api/technicians/me").session(incompleteTechnician)).andExpect(status().isNotFound());
    }

    @Test
    void inactiveTechnicianMustMoveToAnActiveBranchBeforeReactivation() throws Exception {
        var firm = login(companyA);
        var path = "/api/technicians/" + t2.getId();
        mvc.perform(putRequest(path, Map.of("displayName", "Bartek", "branchId", a2.getId(), "active", false))
                .session(firm).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/branches/" + a2.getId()).session(firm).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(putRequest(path, Map.of("displayName", "Nie zapisze się", "branchId", a2.getId(), "active", true))
                .session(firm).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get(path).session(firm)).andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Bartek"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(putRequest(path, Map.of("displayName", "Bartek", "branchId", a1.getId(), "active", true))
                .session(firm).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.branchId").value(a1.getId().toString()))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
    }

    private ResultActions createBranch(MockHttpSession firm, String name) throws Exception {
        return mvc.perform(post("/api/branches").session(firm).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("name", name))));
    }

    private ResultActions createTechnician(MockHttpSession firm, UUID branchId, String email) throws Exception {
        var data = Map.of("email", email, "branchId", branchId, "displayName", "Serwisant testu", "password", PASSWORD);
        return mvc.perform(post("/api/technicians").session(firm).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(data)));
    }

    private MockHttpSession loginWithPassword(String email, String password) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).header("Accept", "application/json")
                .param("username", email).param("password", password)).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
