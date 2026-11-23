package serwio.flow;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        useMainMethod = SpringBootTest.UseMainMethod.ALWAYS, properties = {
        "server.address=127.0.0.1",
        "app.database.temporary=${SERWIO_E2E_H2:true}",
        "app.demo.seed.enabled=true",
        "app.demo.seed.password=company-e2e-fixture-password",
        "spring.docker.compose.enabled=false",
        "spring.datasource.url=${SERWIO_E2E_DB_URL:jdbc:postgresql://127.0.0.1:1/serwio_e2e}",
        "spring.datasource.username=${SERWIO_E2E_DB_USER:serwio_e2e}",
        "spring.datasource.password=${SERWIO_E2E_DB_PASSWORD:serwio_e2e}",
        "spring.jpa.hibernate.ddl-auto=update"
})
class ServiceOrderFlowTest {
    private static final String COMPANY_PASSWORD = "company-e2e-fixture-password";
    private static final String OWNER_PASSWORD = "owner-e2e-fixture-password";
    private static final String TEMPORARY_PASSWORD = "tech-e2e-temporary-password";
    private static final String TECHNICIAN_PASSWORD = "tech-e2e-fixture-password";
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired @Qualifier("seedDemoLocation") CommandLineRunner seedDemoLocation;
    @MockitoBean Clock visitClock;

    @Test
    void seedingAgainKeepsExistingRecordsAndRepairsAnOldDemoLogin() throws Exception {
        var ownerCount = jdbc.queryForObject("select count(*) from asset_owner", Long.class);
        var branchCount = jdbc.queryForObject("select count(*) from service_branch", Long.class);
        var objectCount = jdbc.queryForObject("select count(*) from service_object", Long.class);
        var technicianCount = jdbc.queryForObject("select count(*) from technician", Long.class);
        assertThat(jdbc.update("update user_account set password_hash = ? where email = ?",
                "!seed-account-without-login", "technician@example.com")).isEqualTo(1);
        seedDemoLocation.run();
        assertThat(jdbc.queryForObject("select count(*) from asset_owner", Long.class)).isEqualTo(ownerCount);
        assertThat(jdbc.queryForObject("select count(*) from service_branch", Long.class)).isEqualTo(branchCount);
        assertThat(jdbc.queryForObject("select count(*) from service_object", Long.class)).isEqualTo(objectCount);
        assertThat(jdbc.queryForObject("select count(*) from technician", Long.class)).isEqualTo(technicianCount);
        var technician = new Api();
        technician.login("technician@example.com", COMPANY_PASSWORD);
        assertThat(technician.get("/api/technicians/me").path("email").asText()).isEqualTo("technician@example.com");
    }

    @ParameterizedTest(name = "Zlecenie {0} od rejestracji do zamknięcia")
    @ValueSource(strings = {"REPAIR", "INSPECTION"})
    void registersOwnerAndCompletesServiceOrderOverHttp(String orderType) throws Exception {
        when(visitClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(visitClock.instant()).thenAnswer(invocation -> Instant.now());
        var suffix = UUID.randomUUID().toString();
        var ownerEmail = "owner-" + suffix + "@example.com";
        var owner = new Api();
        owner.post("/api/auth/register/owner", 201, Map.of("firstName", "Joanna", "lastName", "Testowa",
                "email", ownerEmail, "password", OWNER_PASSWORD, "confirmPassword", OWNER_PASSWORD));
        owner.login(ownerEmail, OWNER_PASSWORD);
        assertThat(owner.get("/api/users/me").path("role").asText()).isEqualTo("ASSET_OWNER");
        assertThat(owner.get("/api/owners/me").path("name").asText()).isEqualTo("Joanna Testowa");

        var locationId = owner.post("/api/locations", 201, Map.of("name", "Lokalizacja audytu",
                "addressLine", "Testowa 1", "city", "Warszawa", "postalCode", "00-001", "country", "PL",
                "timezone", WARSAW.getId())).path("id").asText();
        var plannedStart = LocalDate.now(WARSAW).plusDays(2).atTime(9, 0).atZone(WARSAW).toOffsetDateTime();
        var revisitStart = plannedStart.plusDays(1);
        owner.put("/api/locations/" + locationId + "/availability", 200, Map.of("days", List.of(
                availabilityDay(plannedStart), availabilityDay(revisitStart))));
        var objectId = owner.post("/api/service-objects", 201, Map.of("locationId", locationId, "name", "Klimatyzator",
                "type", "AC", "serialNumber", suffix)).path("id").asText();
        assertThat(owner.get("/api/service-objects/" + objectId).path("locationId").asText()).isEqualTo(locationId);

        var company = new Api();
        company.login("company@example.com", COMPANY_PASSWORD);
        var branchId = company.post("/api/branches", 201, Map.of("name", "Oddział " + suffix)).path("id").asText();
        var technicianEmail = "tech-" + suffix + "@example.com";
        var technicianId = company.post("/api/technicians", 201, Map.of("email", technicianEmail,
                "displayName", "Serwisant audytu", "branchId", branchId, "password", TEMPORARY_PASSWORD)).path("id").asText();
        var technician = new Api();
        technician.login(technicianEmail, TEMPORARY_PASSWORD);
        assertThat(technician.get("/api/users/me").path("passwordChangeRequired").asBoolean()).isTrue();
        technician.put("/api/users/me/password", 204, Map.of("currentPassword", TEMPORARY_PASSWORD,
                "newPassword", TECHNICIAN_PASSWORD));
        technician = new Api();
        technician.login(technicianEmail, TECHNICIAN_PASSWORD);
        assertThat(technician.get("/api/users/me").path("passwordChangeRequired").asBoolean()).isFalse();

        var order = owner.post("/api/service-orders", 201, Map.of("serviceObjectId", objectId, "orderType", orderType,
                "description", "Urządzenie nie chłodzi."));
        var orderId = order.path("id").asText();
        var orderPath = "/api/service-orders/" + orderId;
        assertThat(order.path("currentStatus").asText()).isEqualTo("NEW");
        assertThat(company.get("/api/service-orders?status=NEW")).anySatisfy(item ->
                assertThat(item.path("id").asText()).isEqualTo(orderId));
        var assignment = company.put(orderPath + "/assignment", 200, Map.of("expectedVersion", 0,
                "branchIds", List.of(branchId), "technicianIds", List.of(), "coordinatorTechnicianId", technicianId));
        assertThat(assignment.path("status").asText()).isEqualTo("ASSIGNED");
        assertThat(technician.get(orderPath).path("coordinator").path("id").asText()).isEqualTo(technicianId);

        var expectedHistory = new ArrayList<>(List.of("NEW", "ASSIGNED"));
        var lastVisitStart = plannedStart;
        if (orderType.equals("INSPECTION")) {
            runVisit(owner, company, technician, orderPath, technicianId, plannedStart, "REVISIT_REQUIRED");
            expectedHistory.addAll(List.of("AWAITING_SCHEDULING", "VISIT_CONFIRMED", "IN_PROGRESS", "REVISIT_REQUIRED"));
            assertThat(company.get(orderPath).path("currentStatus").asText()).isEqualTo("REVISIT_REQUIRED");
            lastVisitStart = revisitStart;
        }
        var completedVisitPath = runVisit(owner, company, technician, orderPath, technicianId, lastVisitStart, "COMPLETED");
        expectedHistory.addAll(List.of("AWAITING_SCHEDULING", "VISIT_CONFIRMED", "IN_PROGRESS", "COMPLETED", "CLOSED"));
        assertThat(company.post(orderPath + "/close", 409, null).path("code").asText()).isEqualTo("REPORT_NOT_FINALIZED");

        var reportPath = orderPath + "/report";
        var draft = technician.put(reportPath, 200, Map.of("actionsDone", "Czyszczenie", "diagnosis", "Zatkany filtr",
                "resolution", "Wyczyszczono filtr"));
        owner.request("GET", reportPath, 404, null, null);
        when(visitClock.instant()).thenReturn(lastVisitStart.plusMinutes(40).toInstant());
        var report = technician.post(reportPath + "/finalize", 200, null);
        assertThat(report.path("id").asText()).isEqualTo(draft.path("id").asText());
        assertThat(technician.put(reportPath, 409, Map.of("diagnosis", "Zmieniona diagnoza"))
                .path("code").asText()).isEqualTo("REPORT_ALREADY_FINALIZED");
        when(visitClock.instant()).thenReturn(lastVisitStart.plusMinutes(45).toInstant());
        var closed = company.post(orderPath + "/close", 200, null);
        assertThat(closed.path("currentStatus").asText()).isEqualTo("CLOSED");
        assertThat(closed.path("reportFinalized").asBoolean()).isTrue();
        assertThat(OffsetDateTime.parse(closed.path("closedAt").asText()).toInstant())
                .isAfter(OffsetDateTime.parse(report.path("finalizedAt").asText()).toInstant());
        for (var reader : List.of(owner, company, technician)) {
            assertThat(reader.get(orderPath).path("currentStatus").asText()).isEqualTo("CLOSED");
            assertThat(reader.get(reportPath).path("diagnosis").asText()).isEqualTo("Zatkany filtr");
        }
        var staffing = Map.of("technicianIds", List.of(technicianId), "coordinatorTechnicianId", technicianId);
        assertThat(company.post(orderPath + "/visits", 409, staffing).path("code").asText())
                .isEqualTo("INVALID_VISIT_STATE");
        company.put(completedVisitPath + "/staffing", 409, staffing);
        company.post(completedVisitPath + "/cancel", 409, null);
        technician.post(completedVisitPath + "/start", 404, null);
        var outsider = new Api();
        outsider.login("owner@example.com", COMPANY_PASSWORD);
        outsider.request("GET", reportPath, 404, null, null);
        assertThat(jdbc.queryForList("select status from order_status_history where service_order_id = ? order by changed_at",
                String.class, UUID.fromString(orderId))).containsExactlyElementsOf(expectedHistory);
    }

    private static Map<String, Object> availabilityDay(OffsetDateTime start) {
        return Map.of("dayOfWeek", start.getDayOfWeek().getValue(), "intervals", List.of(Map.of("from", "08:00", "to", "16:00")));
    }

    private String runVisit(Api owner, Api company, Api technician, String orderPath, String technicianId,
                            OffsetDateTime start, String outcome) throws Exception {
        var visit = company.post(orderPath + "/visits", 201, Map.of("technicianIds", List.of(technicianId),
                "coordinatorTechnicianId", technicianId));
        var visitPath = orderPath + "/visits/" + visit.path("id").asText();
        assertThat(visit.path("status").asText()).isEqualTo("DRAFT");
        var proposed = technician.post(visitPath + "/proposals", 200,
                Map.of("startsAt", start.toString(), "endsAt", start.plusHours(1).toString()));
        var acceptPath = visitPath + "/proposals/" + proposed.path("currentProposal").path("id").asText() + "/accept";
        technician.post(acceptPath, 403, null);
        assertThat(owner.post(acceptPath, 200, null).path("status").asText()).isEqualTo("CONFIRMED");
        when(visitClock.instant()).thenReturn(start.toInstant());
        assertThat(technician.post(visitPath + "/start", 200, null).path("status").asText()).isEqualTo("IN_PROGRESS");
        when(visitClock.instant()).thenReturn(start.plusMinutes(30).toInstant());
        assertThat(technician.post(visitPath + "/complete", 200, Map.of("outcome", outcome))
                .path("outcome").asText()).isEqualTo(outcome);
        return visitPath;
    }

    private final class Api {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

        void login(String email, String password) throws Exception {
            request("POST", "/login", 200, "username=" + encode(email) + "&password=" + encode(password),
                    "application/x-www-form-urlencoded");
        }

        JsonNode get(String path) throws Exception {
            return request("GET", path, 200, null, null);
        }

        JsonNode post(String path, int status, Map<String, ?> data) throws Exception {
            return sendJson("POST", path, status, data);
        }

        JsonNode put(String path, int status, Map<String, ?> data) throws Exception {
            return sendJson("PUT", path, status, data);
        }

        private JsonNode sendJson(String method, String path, int status, Map<String, ?> data) throws Exception {
            return request(method, path, status, data == null ? null : mapper.writeValueAsString(data), "application/json");
        }

        private JsonNode request(String method, String path, int status, String data, String contentType) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(10)).header("Accept", "application/json");
            if (!method.equals("GET")) {
                var token = get("/api/csrf");
                builder.header(token.path("headerName").asText(), token.path("token").asText());
            }
            if (data != null) builder.header("Content-Type", contentType);
            var request = builder.method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(data)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
            return response.body().isBlank() ? null : mapper.readTree(response.body());
        }

        private String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        }
    }
}
