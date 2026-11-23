package serwio.testsupport;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.asset.model.ServiceObject;
import serwio.asset.repository.ServiceObjectRepository;
import serwio.company.model.ServiceBranch;
import serwio.company.model.ServiceCompany;
import serwio.company.repository.ServiceBranchRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.order.model.ServiceOrder;
import serwio.order.repository.ServiceOrderRepository;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;
import serwio.report.repository.ServiceReportRepository;
import serwio.technician.model.Technician;
import serwio.technician.model.TechnicianBranchHistory;
import serwio.technician.repository.TechnicianBranchHistoryRepository;
import serwio.technician.repository.TechnicianRepository;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.repository.VisitProposalRepository;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.temporary=true", "app.demo.seed.enabled=false"})
@AutoConfigureMockMvc
public abstract class OrderApiFixture {
    protected static final String PASSWORD = "test-password-123";
    protected static final String ORDERS_PATH = "/api/service-orders";

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected PlatformTransactionManager transactionManager;
    @Autowired protected UserAccountRepository accounts;
    @Autowired protected AssetOwnerRepository owners;
    @Autowired protected LocationRepository locations;
    @Autowired protected ServiceObjectRepository objects;
    @Autowired protected ServiceCompanyRepository companies;
    @Autowired protected ServiceBranchRepository branches;
    @Autowired protected TechnicianRepository technicians;
    @Autowired protected TechnicianBranchHistoryRepository branchHistory;
    @Autowired protected ServiceOrderRepository orders;
    @Autowired protected ServiceReportRepository reports;
    @Autowired protected ServiceVisitRepository visits;
    @Autowired protected VisitProposalRepository proposals;
    @MockitoBean protected Clock visitClock;

    /** Firma A z dwoma aktywnymi i jednym nieaktywnym oddziałem, firma B z jednym oddziałem. */
    protected UserAccount companyA, companyB, owner, technicianLogin;
    protected ServiceCompany firmA, firmB;
    protected ServiceObject serviceObject;
    protected ServiceBranch a1, a2, aInactive, b1;
    protected Technician t1, t2, tInactive, tBlockedAccount, tb1;
    protected UUID orderId;

    @BeforeEach
    protected void setUp() {
        when(visitClock.getZone()).thenReturn(ZoneOffset.UTC);
        when(visitClock.instant()).thenAnswer(invocation -> Instant.now());
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            companyA = account("SERVICE_COMPANY", "ACTIVE");
            companyB = account("SERVICE_COMPANY", "ACTIVE");
            owner = account("ASSET_OWNER", "ACTIVE");
            firmA = company(companyA);
            firmB = company(companyB);
            a1 = branch(firmA, "A1 Warszawa", true);
            a2 = branch(firmA, "A2 Kraków", true);
            aInactive = branch(firmA, "A3 zamknięty", false);
            b1 = branch(firmB, "B1 obca", true);
            technicianLogin = account("TECHNICIAN", "ACTIVE");
            t1 = technician(technicianLogin, a1, "Anna A1", true);
            t2 = technician(account("TECHNICIAN", "ACTIVE"), a2, "Bartek A2", true);
            tInactive = technician(account("TECHNICIAN", "ACTIVE"), a1, "Celina nieaktywna", false);
            tBlockedAccount = technician(account("TECHNICIAN", "BLOCKED"), a1, "Damian zablokowany", true);
            tb1 = technician(account("TECHNICIAN", "ACTIVE"), b1, "Ewa obca", true);
            var order = order(firmA, "NEW");
            orderId = order.getId();
            serviceObject = order.getServiceObject();
        });
    }

    protected ResultActions visitWrite(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                     MockHttpSession session, Map<String, ?> body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body)));
    }

    protected UUID createDraft(UUID id, MockHttpSession session, List<UUID> staff, UUID coordinator) throws Exception {
        put(session, body(0, List.of(), staff, coordinator), id).andExpect(status().isOk());
        return postDraft(id, session, staff, coordinator);
    }

    protected UUID postDraft(UUID id, MockHttpSession session, List<UUID> staff, UUID coordinator) throws Exception {
        var result = visitWrite(post(orderPath(id) + "/visits"), session,
                Map.of("technicianIds", staff, "coordinatorTechnicianId", coordinator))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    protected void setTuesdayHours(MockHttpSession session, UUID locationId, String from, String to) throws Exception {
        mvc.perform(putRequest("/api/locations/" + locationId + "/availability",
                Map.of("days", List.of(Map.of("dayOfWeek", 2, "intervals", List.of(Map.of("from", from, "to", to))))))
                .session(session).with(csrf())).andExpect(status().isOk());
    }

    protected static OffsetDateTime nextTuesday(int hour) {
        var zone = ZoneId.of("Europe/Warsaw");
        return LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).atTime(hour, 0)
                .atZone(zone).toOffsetDateTime();
    }

    protected static Map<String, ?> timeRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return Map.of("startsAt", startsAt.toString(), "endsAt", endsAt.toString());
    }

    protected UUID proposalId(MvcResult result) throws Exception {
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .path("currentProposal").path("id").asText());
    }

    protected void moveTechnician(MockHttpSession session, Technician technician, ServiceBranch branch) throws Exception {
        mvc.perform(putRequest("/api/technicians/" + technician.getId(),
                Map.of("branchId", branch.getId(), "displayName", technician.getDisplayName(), "active", true))
                .session(session).with(csrf())).andExpect(status().isOk());
    }

    protected UUID confirmVisit(MockHttpSession company, List<UUID> staff, Technician coordinator,
                              OffsetDateTime planned) throws Exception {
        put(company, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        var visitId = postDraft(orderId, company, staff, coordinator.getId());
        confirmDraft(visitId, coordinator, planned);
        return visitId;
    }

    protected void confirmDraft(UUID visitId, Technician coordinator, OffsetDateTime planned) throws Exception {
        var ownerSession = login(owner);
        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        var path = orderPath(orderId) + "/visits/" + visitId + "/proposals";
        var proposal = propose(path, login(coordinator.getUser()), planned);
        mvc.perform(post(path + "/" + proposal + "/accept").session(ownerSession).with(csrf()))
                .andExpect(status().isOk());
    }

    protected ResultActions orderAction(UUID id, String action, MockHttpSession session) throws Exception {
        return mvc.perform(post(orderPath(id) + "/" + action).session(session).with(csrf()));
    }

    protected UUID propose(String path, MockHttpSession coordinator, OffsetDateTime start) throws Exception {
        return proposalId(visitWrite(post(path), coordinator, timeRange(start, start.plusHours(1)))
                .andExpect(status().isOk()).andReturn());
    }

    protected static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var futures = List.of(first, second).stream().map(call -> executor.submit(() -> {
                ready.countDown();
                start.await();
                return call.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    protected ResultActions put(MockHttpSession session, Map<String, Object> data) throws Exception {
        return put(session, data, orderId);
    }

    protected ResultActions put(MockHttpSession session, Map<String, Object> data, UUID id) throws Exception {
        return mvc.perform(putRequest(path(id), data).session(session).with(csrf()));
    }

    protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putRequest(
            String url, Map<String, ?> data) throws Exception {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType("application/json").content(mapper.writeValueAsString(data));
    }

    protected static Map<String, Object> body(long version, List<UUID> branchIds, List<UUID> technicianIds,
                                            UUID coordinator) {
        var data = new HashMap<String, Object>();
        data.put("expectedVersion", version);
        data.put("branchIds", branchIds);
        data.put("technicianIds", technicianIds);
        data.put("coordinatorTechnicianId", coordinator);
        return data;
    }

    protected static String path(UUID id) {
        return orderPath(id) + "/assignment";
    }

    protected static String orderPath(UUID id) {
        return ORDERS_PATH + "/" + id;
    }

    protected int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where service_order_id = ?", Integer.class, orderId);
    }

    protected int activeCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table
                + " where service_order_id = ? and unassigned_at is null", Integer.class, orderId);
    }

    protected List<String> statusHistory() {
        return jdbc.queryForList("select status from order_status_history where service_order_id = ? order by changed_at",
                String.class, orderId);
    }

    protected String currentStatus() {
        return jdbc.queryForObject("select current_status from service_order where id = ?", String.class, orderId);
    }

    protected MockHttpSession login(UserAccount account) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).header("Accept", "application/json")
                        .param("username", account.getEmail()).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    protected UserAccount account(String role, String status) {
        var account = new UserAccount();
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setRole(role);
        account.setStatus(status);
        account.setCreatedAt(now());
        return accounts.save(account);
    }

    protected ServiceCompany company(UserAccount account) {
        var company = new ServiceCompany();
        company.setUser(account);
        company.setName("Firma " + account.getEmail());
        company.setCreatedAt(now());
        return companies.save(company);
    }

    protected ServiceBranch branch(ServiceCompany company, String name, boolean active) {
        var branch = new ServiceBranch();
        branch.setServiceCompany(company);
        branch.setName(name);
        branch.setActive(active);
        branch.setCreatedAt(now());
        return branches.save(branch);
    }

    protected Technician technician(UserAccount account, ServiceBranch branch, String name, boolean active) {
        var technician = new Technician();
        technician.setUser(account);
        technician.setBranch(branch);
        technician.setDisplayName(name);
        technician.setActive(active);
        technician.setCreatedAt(now());
        technicians.save(technician);
        var membership = new TechnicianBranchHistory();
        membership.setTechnician(technician);
        membership.setBranch(branch);
        membership.setChangedByUser(branch.getServiceCompany().getUser());
        membership.setValidFrom(technician.getCreatedAt());
        branchHistory.save(membership);
        return technician;
    }

    protected ServiceOrder order(ServiceCompany company, String status) {
        var assetOwner = new AssetOwner();
        assetOwner.setUser(owner);
        assetOwner.setName("Właściciel");
        assetOwner.setCreatedAt(now());
        owners.save(assetOwner);
        return newOrder(company, status, objectAtNewLocation(assetOwner, "Lokalizacja"));
    }

    protected ServiceObject objectAtNewLocation(AssetOwner assetOwner, String name) {
        var location = new Location();
        location.setAssetOwner(assetOwner);
        location.setName(name);
        location.setAddressLine("Testowa 1");
        location.setCity("Warszawa");
        location.setPostalCode("00-001");
        location.setCountry("PL");
        location.setTimezone("Europe/Warsaw");
        location.setCreatedAt(now());
        locations.save(location);
        var object = new ServiceObject();
        object.setLocation(location);
        object.setName("Klimatyzator");
        object.setType("AC");
        object.setActive(true);
        object.setCreatedAt(now());
        return objects.save(object);
    }

    protected ServiceOrder newOrder(ServiceCompany company, String status, ServiceObject object) {
        var order = new ServiceOrder();
        order.setServiceObject(object);
        order.setServiceCompany(company);
        order.setCreatedByUser(owner);
        order.setOrderType("REPAIR");
        order.setDescription("Nie chłodzi");
        order.setCurrentStatus(status);
        order.setCreatedAt(now());
        return orders.save(order);
    }

    protected static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
