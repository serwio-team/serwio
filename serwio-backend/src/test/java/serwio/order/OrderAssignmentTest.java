package serwio.order;

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
import org.junit.jupiter.api.Test;
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
import serwio.report.model.ServiceReport;
import serwio.report.repository.ServiceReportRepository;
import serwio.technician.model.Technician;
import serwio.technician.repository.TechnicianRepository;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitProposal;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.repository.VisitProposalRepository;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.database.temporary=true", "app.demo.seed.enabled=false"})
@AutoConfigureMockMvc
class OrderAssignmentTest {
    private static final String PASSWORD = "test-password-123";
    private static final String ORDERS_PATH = "/api/service-orders";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired UserAccountRepository accounts;
    @Autowired AssetOwnerRepository owners;
    @Autowired LocationRepository locations;
    @Autowired ServiceObjectRepository objects;
    @Autowired ServiceCompanyRepository companies;
    @Autowired ServiceBranchRepository branches;
    @Autowired TechnicianRepository technicians;
    @Autowired ServiceOrderRepository orders;
    @Autowired ServiceReportRepository reports;
    @Autowired ServiceVisitRepository visits;
    @Autowired VisitProposalRepository proposals;
    @MockitoBean Clock visitClock;

    /** Firma A z dwoma aktywnymi i jednym nieaktywnym oddziałem, firma B z jednym oddziałem. */
    UserAccount companyA, companyB, owner, technicianLogin;
    ServiceCompany firmA, firmB;
    ServiceObject serviceObject;
    ServiceBranch a1, a2, aInactive, b1;
    Technician t1, t2, tInactive, tBlockedAccount, tb1;
    UUID orderId;

    @BeforeEach
    void setUp() {
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

    @Test
    void visitCandidatesUseCurrentAssignmentsAndOnlyCurrentCompanyCanRead() throws Exception {
        var firmSession = login(companyA);
        var candidatesPath = orderPath(orderId) + "/visits/candidates";
        mvc.perform(get(candidatesPath).session(firmSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        put(firmSession, body(0, List.of(a1.getId()), List.of(t1.getId(), t2.getId()), t1.getId()))
                .andExpect(status().isOk());
        mvc.perform(get(candidatesPath).session(firmSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(t1.getId().toString(), t2.getId().toString())))
                .andExpect(jsonPath("$[0].displayName").value(t1.getDisplayName()))
                .andExpect(jsonPath("$[0].branchId").value(a1.getId().toString()));

        put(firmSession, body(1, List.of(), List.of(t2.getId()), t2.getId())).andExpect(status().isOk());
        mvc.perform(get(candidatesPath).session(firmSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(t2.getId().toString())));
        mvc.perform(get(candidatesPath).session(login(companyB))).andExpect(status().isNotFound());
        mvc.perform(get(candidatesPath).session(login(owner))).andExpect(status().isForbidden());
        mvc.perform(get(candidatesPath).session(login(technicianLogin))).andExpect(status().isForbidden());
        mvc.perform(get(candidatesPath)).andExpect(status().isUnauthorized());
    }

    @Test
    void visitReadUsesOrderPermissionsAndReturnsLocationAvailability() throws Exception {
        var firmSession = login(companyA);
        var ownerSession = login(owner);
        var technicianSession = login(technicianLogin);
        var visitsPath = orderPath(orderId) + "/visits";
        mvc.perform(putRequest("/api/locations/" + serviceObject.getLocation().getId() + "/availability",
                Map.of("days", List.of(Map.of("dayOfWeek", 2, "intervals", List.of(Map.of("from", "08:00", "to", "16:00"))))))
                .with(csrf()).session(ownerSession)).andExpect(status().isOk());
        mvc.perform(get(visitsPath).session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.availability[0].dayOfWeek").value(2))
                .andExpect(jsonPath("$.availability[0].intervals[0].from").value("08:00"))
                .andExpect(jsonPath("$.visits").isEmpty());
        mvc.perform(get(visitsPath).session(technicianSession)).andExpect(status().isNotFound());
        mvc.perform(get(visitsPath).session(login(companyB))).andExpect(status().isNotFound());
        mvc.perform(get(visitsPath)).andExpect(status().isUnauthorized());

        put(firmSession, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        var visit = new ServiceVisit();
        visit.setServiceOrder(orders.findById(orderId).orElseThrow());
        visit.setCoordinatorTechnician(t1);
        visit.setStatus("DRAFT");
        visit.setCreatedAt(now());
        visits.save(visit);
        for (var session : List.of(firmSession, ownerSession, technicianSession)) {
            mvc.perform(get(visitsPath).session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.visits[0].id").value(visit.getId().toString()))
                    .andExpect(jsonPath("$.visits[0].status").value("DRAFT"))
                    .andExpect(jsonPath("$.visits[0].coordinatorTechnicianId").value(t1.getId().toString()))
                    .andExpect(jsonPath("$.visits[0].currentProposal").value(nullValue()));
        }
    }

    @Test
    void visitCreateValidatesStaffAndRecordsOneDraftAndTransition() throws Exception {
        var firmSession = login(companyA);
        var visitsPath = orderPath(orderId) + "/visits";
        var selected = Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId());
        visitWrite(post(visitsPath), firmSession, selected).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_STATE"));
        put(firmSession, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());

        visitWrite(post(visitsPath), firmSession,
                Map.of("technicianIds", List.of(t1.getId(), t1.getId()), "coordinatorTechnicianId", t1.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.technicianIds").isArray());
        visitWrite(post(visitsPath), firmSession,
                Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t2.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.coordinatorTechnicianId").isArray());
        visitWrite(post(visitsPath), firmSession,
                Map.of("technicianIds", List.of(t2.getId()), "coordinatorTechnicianId", t2.getId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.technicianIds").isArray());
        visitWrite(post(visitsPath), login(owner), selected).andExpect(status().isForbidden());
        visitWrite(post(visitsPath), login(companyB), selected).andExpect(status().isNotFound());

        visitWrite(post(visitsPath), firmSession, selected).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.technicians[*].id", contains(t1.getId().toString())));
        visitWrite(post(visitsPath), firmSession, selected).andExpect(status().isConflict());
        mvc.perform(get(visitsPath).session(firmSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits.length()").value(1));
        assertThat(currentStatus()).isEqualTo("AWAITING_SCHEDULING");
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING");
        put(firmSession, body(1, List.of(a1.getId(), a2.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        assertThat(currentStatus()).isEqualTo("AWAITING_SCHEDULING");
    }

    private ResultActions visitWrite(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                     MockHttpSession session, Map<String, ?> body) throws Exception {
        return mvc.perform(request.session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body)));
    }

    @Test
    void visitStaffingChangeExpiresPendingProposalAndPreservesItsHistory() throws Exception {
        var firmSession = login(companyA);
        var visitsPath = orderPath(orderId) + "/visits";
        put(firmSession, body(0, List.of(a1.getId(), a2.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        var firstStaff = Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId());
        var created = visitWrite(post(visitsPath), firmSession, firstStaff).andExpect(status().isCreated()).andReturn();
        var visitId = UUID.fromString(mapper.readTree(created.getResponse().getContentAsString()).get("id").asText());
        var proposal = new VisitProposal();
        proposal.setVisit(visits.findById(visitId).orElseThrow());
        proposal.setProposedByUser(technicianLogin);
        proposal.setProposedStartsAt(now().plusDays(1));
        proposal.setProposedEndsAt(proposal.getProposedStartsAt().plusHours(1));
        proposal.setCreatedAt(now());
        proposal.setStatus("PENDING");
        proposals.save(proposal);

        var path = visitsPath + "/" + visitId + "/staffing";
        visitWrite(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), firmSession, firstStaff)
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentProposal.id").value(proposal.getId().toString()));
        var newStaff = Map.of("technicianIds", List.of(t2.getId()), "coordinatorTechnicianId", t2.getId());
        visitWrite(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(path), firmSession, newStaff)
                .andExpect(status().isOk()).andExpect(jsonPath("$.technicians[*].id", contains(t2.getId().toString())))
                .andExpect(jsonPath("$.coordinatorTechnicianId").value(t2.getId().toString()))
                .andExpect(jsonPath("$.currentProposal").value(nullValue()));
        assertThat(proposals.findById(proposal.getId()).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        var anotherOrder = newOrder(firmA, "ASSIGNED", serviceObject);
        visitWrite(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                orderPath(anotherOrder.getId()) + "/visits/" + visitId + "/staffing"), firmSession, firstStaff)
                .andExpect(status().isNotFound());
    }

    @Test
    void visitCoordinatorProposesOnlyOnceAndOnlyWithinCurrentHours() throws Exception {
        var firmSession = login(companyA);
        var ownerSession = login(owner);
        var coordinatorSession = login(technicianLogin);
        var visitId = createDraft(orderId, firmSession, List.of(t1.getId(), t2.getId()), t1.getId());
        var path = orderPath(orderId) + "/visits/" + visitId + "/proposals";
        var startsAt = nextTuesday(9);
        var range = timeRange(startsAt, startsAt.plusHours(1));
        visitWrite(post(path), ownerSession, range).andExpect(status().isForbidden());
        visitWrite(post(path), login(t2.getUser()), range).andExpect(status().isForbidden());
        visitWrite(post(path), coordinatorSession, range).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_LOCATION_AVAILABILITY"));

        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        visitWrite(post(path), coordinatorSession, timeRange(startsAt.withOffsetSameLocal(ZoneOffset.UTC), startsAt.plusHours(1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.startsAt").isArray());
        visitWrite(post(path), coordinatorSession, range).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentProposal.proposedBy").value("COORDINATOR"))
                .andExpect(jsonPath("$.startsAt").value(nullValue()));
        visitWrite(post(path), coordinatorSession, range).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_STATE"));
    }

    private UUID createDraft(UUID id, MockHttpSession session, List<UUID> staff, UUID coordinator) throws Exception {
        put(session, body(0, List.of(), staff, coordinator), id).andExpect(status().isOk());
        return postDraft(id, session, staff, coordinator);
    }

    private UUID postDraft(UUID id, MockHttpSession session, List<UUID> staff, UUID coordinator) throws Exception {
        var result = visitWrite(post(orderPath(id) + "/visits"), session,
                Map.of("technicianIds", staff, "coordinatorTechnicianId", coordinator))
                .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private void setTuesdayHours(MockHttpSession session, UUID locationId, String from, String to) throws Exception {
        mvc.perform(putRequest("/api/locations/" + locationId + "/availability",
                Map.of("days", List.of(Map.of("dayOfWeek", 2, "intervals", List.of(Map.of("from", from, "to", to))))))
                .session(session).with(csrf())).andExpect(status().isOk());
    }

    private static OffsetDateTime nextTuesday(int hour) {
        var zone = ZoneId.of("Europe/Warsaw");
        return LocalDate.now(zone).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).atTime(hour, 0)
                .atZone(zone).toOffsetDateTime();
    }

    private static Map<String, ?> timeRange(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        return Map.of("startsAt", startsAt.toString(), "endsAt", endsAt.toString());
    }

    @Test
    void visitCounterAndRejectKeepHistoryAndNeverApplyAnOldProposal() throws Exception {
        var ownerSession = login(owner);
        var coordinatorSession = login(technicianLogin);
        var visitId = createDraft(orderId, login(companyA), List.of(t1.getId()), t1.getId());
        var path = orderPath(orderId) + "/visits/" + visitId + "/proposals";
        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        var suggested = nextTuesday(9);
        var initial = visitWrite(post(path), coordinatorSession, timeRange(suggested, suggested.plusHours(1)))
                .andExpect(status().isOk()).andReturn();
        var first = proposalId(initial);
        mvc.perform(post(path + "/" + first + "/reject").session(coordinatorSession).with(csrf()))
                .andExpect(status().isForbidden());

        var counter = visitWrite(post(path + "/" + first + "/counter"), ownerSession,
                timeRange(suggested.plusHours(2), suggested.plusHours(3))).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentProposal.proposedBy").value("OWNER")).andReturn();
        var second = proposalId(counter);
        assertThat(second).isNotEqualTo(first);
        mvc.perform(post(path + "/" + first + "/reject").session(ownerSession).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_PROPOSAL"));
        mvc.perform(post(path + "/" + second + "/reject").session(coordinatorSession).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentProposal").value(nullValue()));
        assertThat(proposals.findById(first).orElseThrow().getStatus()).isEqualTo("COUNTERED");
        assertThat(proposals.findById(second).orElseThrow().getStatus()).isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("select responded_by_user_id from visit_proposal where id = ?", UUID.class, second))
                .isEqualTo(technicianLogin.getId());
        assertThat(currentStatus()).isEqualTo("AWAITING_SCHEDULING");
    }

    private UUID proposalId(MvcResult result) throws Exception {
        return UUID.fromString(mapper.readTree(result.getResponse().getContentAsString())
                .path("currentProposal").path("id").asText());
    }

    @Test
    void visitAcceptanceRechecksWindowAndStaffThenConfirmsAtomically() throws Exception {
        var firmSession = login(companyA);
        var ownerSession = login(owner);
        var coordinatorSession = login(technicianLogin);
        put(firmSession, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        var visitId = postDraft(orderId, firmSession, List.of(t1.getId(), t2.getId()), t1.getId());
        var locationId = serviceObject.getLocation().getId();
        setTuesdayHours(ownerSession, locationId, "08:00", "16:00");
        var starts = nextTuesday(9);
        var path = orderPath(orderId) + "/visits/" + visitId + "/proposals";
        var proposed = visitWrite(post(path), coordinatorSession, timeRange(starts, starts.plusHours(1)))
                .andExpect(status().isOk()).andReturn();
        var proposalId = proposalId(proposed);
        var acceptance = path + "/" + proposalId + "/accept";
        mvc.perform(post(acceptance).session(coordinatorSession).with(csrf())).andExpect(status().isForbidden());

        setTuesdayHours(ownerSession, locationId, "12:00", "16:00");
        mvc.perform(post(acceptance).session(ownerSession).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OUTSIDE_LOCATION_AVAILABILITY"));
        setTuesdayHours(ownerSession, locationId, "08:00", "16:00");
        moveTechnician(firmSession, t2, a1);
        mvc.perform(post(acceptance).session(ownerSession).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_STATE"));
        assertThat(proposals.findById(proposalId).orElseThrow().getStatus()).isEqualTo("PENDING");

        moveTechnician(firmSession, t2, a2);
        mvc.perform(post(acceptance).session(ownerSession).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currentProposal").value(nullValue()));
        assertThat(visits.findById(visitId).orElseThrow().getStartsAt().toInstant()).isEqualTo(starts.toInstant());
        assertThat(proposals.findById(proposalId).orElseThrow().getStatus()).isEqualTo("ACCEPTED");
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING", "VISIT_CONFIRMED");
        mvc.perform(post(acceptance).session(ownerSession).with(csrf())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_PROPOSAL"));
        put(firmSession, body(1, List.of(), List.of(t2.getId()), t2.getId())).andExpect(status().isConflict());
    }

    private void moveTechnician(MockHttpSession session, Technician technician, ServiceBranch branch) throws Exception {
        mvc.perform(putRequest("/api/technicians/" + technician.getId(),
                Map.of("branchId", branch.getId(), "displayName", technician.getDisplayName(), "active", true))
                .session(session).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void visitExecutionRecordsActualTimesResultAndOneHistoryEntryPerAction() throws Exception {
        var company = login(companyA);
        var coordinator = login(technicianLogin);
        var planned = nextTuesday(9);
        var visitId = confirmVisit(company, List.of(t1.getId()), t1, planned);
        var visitPath = orderPath(orderId) + "/visits/" + visitId;
        var outcome = Map.of("outcome", "COMPLETED");

        visitWrite(post(visitPath + "/complete"), coordinator, outcome).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_VISIT_STATE"));
        visitWrite(post(visitPath + "/complete"), coordinator, Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.outcome").isArray());
        visitWrite(post(visitPath + "/complete"), coordinator, Map.of("outcome", "OTHER"))
                .andExpect(status().isBadRequest());

        var started = planned.plusMinutes(10);
        when(visitClock.instant()).thenReturn(started.toInstant());
        mvc.perform(post(visitPath + "/start").session(coordinator).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startedAt").isString()).andExpect(jsonPath("$.outcome").value(nullValue()));
        mvc.perform(post(visitPath + "/start").session(coordinator).with(csrf())).andExpect(status().isConflict());
        assertThat(currentStatus()).isEqualTo("IN_PROGRESS");

        var completed = started.plusMinutes(20);
        when(visitClock.instant()).thenReturn(completed.toInstant());
        visitWrite(post(visitPath + "/complete"), coordinator, outcome).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("COMPLETED"));
        visitWrite(post(visitPath + "/complete"), coordinator, outcome).andExpect(status().isConflict());
        mvc.perform(get(orderPath(orderId) + "/visits").session(login(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits[0].outcome").value("COMPLETED"));
        var stored = visits.findById(visitId).orElseThrow();
        assertThat(stored.getStartsAt().toInstant()).isEqualTo(planned.toInstant());
        assertThat(stored.getEndsAt().toInstant()).isEqualTo(planned.plusHours(1).toInstant());
        assertThat(stored.getStartedAt().toInstant()).isEqualTo(started.toInstant());
        assertThat(stored.getCompletedAt().toInstant()).isEqualTo(completed.toInstant());
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING", "VISIT_CONFIRMED",
                "IN_PROGRESS", "COMPLETED");
        visitWrite(post(orderPath(orderId) + "/visits"), company,
                Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void visitExecutionRequiresTheVisitCoordinatorWithCurrentAccess() throws Exception {
        var company = login(companyA);
        var coordinator = login(t2.getUser());
        var visitId = confirmVisit(company, List.of(t1.getId(), t2.getId()), t2, nextTuesday(9));
        var startPath = orderPath(orderId) + "/visits/" + visitId + "/start";

        mvc.perform(post(startPath).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post(startPath).session(coordinator)).andExpect(status().isForbidden());
        for (var user : List.of(owner, companyA, technicianLogin)) {
            mvc.perform(post(startPath).session(login(user)).with(csrf())).andExpect(status().isForbidden());
        }
        var otherOrderId = newOrder(firmA, "NEW", serviceObject).getId();
        put(company, body(0, List.of(a2.getId()), List.of(), t2.getId()), otherOrderId).andExpect(status().isOk());
        mvc.perform(post(orderPath(otherOrderId) + "/visits/" + visitId + "/start")
                .session(coordinator).with(csrf())).andExpect(status().isNotFound());
        moveTechnician(company, t2, a1);
        mvc.perform(post(startPath).session(coordinator).with(csrf())).andExpect(status().isNotFound());
        assertThat(currentStatus()).isEqualTo("VISIT_CONFIRMED");
    }

    private UUID confirmVisit(MockHttpSession company, List<UUID> staff, Technician coordinator,
                              OffsetDateTime planned) throws Exception {
        put(company, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        var visitId = postDraft(orderId, company, staff, coordinator.getId());
        confirmDraft(visitId, coordinator, planned);
        return visitId;
    }

    private void confirmDraft(UUID visitId, Technician coordinator, OffsetDateTime planned) throws Exception {
        var ownerSession = login(owner);
        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        var path = orderPath(orderId) + "/visits/" + visitId + "/proposals";
        var proposal = propose(path, login(coordinator.getUser()), planned);
        mvc.perform(post(path + "/" + proposal + "/accept").session(ownerSession).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visitRevisitAllowsNewStaffAndRestoresRevisitStateWhenReplacementIsCancelled() throws Exception {
        var company = login(companyA);
        var firstCoordinator = login(technicianLogin);
        var firstStart = nextTuesday(9);
        var firstVisit = confirmVisit(company, List.of(t1.getId()), t1, firstStart);
        var base = orderPath(orderId) + "/visits/";
        when(visitClock.instant()).thenReturn(firstStart.toInstant());
        mvc.perform(post(base + firstVisit + "/start").session(firstCoordinator).with(csrf()))
                .andExpect(status().isOk());
        when(visitClock.instant()).thenReturn(firstStart.plusMinutes(30).toInstant());
        visitWrite(post(base + firstVisit + "/complete"), firstCoordinator, Map.of("outcome", "REVISIT_REQUIRED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.outcome").value("REVISIT_REQUIRED"));
        assertThat(currentStatus()).isEqualTo("REVISIT_REQUIRED");

        put(company, body(1, List.of(), List.of(t2.getId()), t2.getId())).andExpect(status().isOk());
        var cancelledReplacement = postDraft(orderId, company, List.of(t2.getId()), t2.getId());
        mvc.perform(post(base + cancelledReplacement + "/cancel").session(company).with(csrf()))
                .andExpect(status().isOk());
        assertThat(currentStatus()).isEqualTo("REVISIT_REQUIRED");
        var replacement = postDraft(orderId, company, List.of(t2.getId()), t2.getId());
        var replacementStart = firstStart.plusHours(3);
        confirmDraft(replacement, t2, replacementStart);

        var nextCoordinator = login(t2.getUser());
        when(visitClock.instant()).thenReturn(replacementStart.toInstant());
        mvc.perform(post(base + replacement + "/start").session(nextCoordinator).with(csrf()))
                .andExpect(status().isOk());
        when(visitClock.instant()).thenReturn(replacementStart.plusMinutes(45).toInstant());
        visitWrite(post(base + replacement + "/complete"), nextCoordinator, Map.of("outcome", "COMPLETED"))
                .andExpect(status().isOk());
        assertThat(currentStatus()).isEqualTo("COMPLETED");
        assertThat(visits.findAllByServiceOrderIdOrderByCreatedAtDescIdAsc(orderId)).hasSize(3);
        mvc.perform(get(orderPath(orderId) + "/visits").session(company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.visits[?(@.id == '%s')].outcome".formatted(firstVisit),
                        contains("REVISIT_REQUIRED")))
                .andExpect(jsonPath("$.visits[?(@.id == '%s')].coordinatorTechnicianId".formatted(firstVisit),
                        contains(t1.getId().toString())));
        assertThat(visits.findById(firstVisit).orElseThrow().getStartsAt().toInstant()).isEqualTo(firstStart.toInstant());
    }

    @Test
    void orderCancellationEndsAssignmentsAndExpiresProposal() throws Exception {
        var company = login(companyA);
        var coordinator = login(technicianLogin);
        var ownerSession = login(owner);
        var newOrder = newOrder(firmA, "NEW", serviceObject).getId();
        orderAction(newOrder, "cancel", company).andExpect(status().isOk());
        put(company, body(0, List.of(a1.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        var draft = postDraft(orderId, company, List.of(t1.getId()), t1.getId());
        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        var proposal = propose(orderPath(orderId) + "/visits/" + draft + "/proposals", coordinator, nextTuesday(9));

        orderAction(orderId, "cancel", ownerSession).andExpect(status().isForbidden());
        orderAction(orderId, "cancel", login(companyB)).andExpect(status().isNotFound());
        orderAction(orderId, "cancel", company).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.closedAt").isString());
        orderAction(orderId, "cancel", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        assertThat(visits.findById(draft).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        assertThat(proposals.findById(proposal).orElseThrow().getStatus()).isEqualTo("EXPIRED");
        var closedAt = orders.findById(orderId).orElseThrow().getClosedAt();
        for (var table : List.of("order_branch_assignment", "order_technician_assignment")) {
            assertThat(activeCount(table)).isZero();
            assertThat(jdbc.queryForList("select unassigned_at from " + table + " where service_order_id = ?",
                    OffsetDateTime.class, orderId)).isNotEmpty().allMatch(ended -> ended.isEqual(closedAt));
        }
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING", "CANCELLED");
    }

    @Test
    void orderCancellationReleasesConfirmedReservation() throws Exception {
        var company = login(companyA);
        var coordinator = login(technicianLogin);
        var ownerSession = login(owner);
        var start = nextTuesday(9);
        var confirmed = confirmVisit(company, List.of(t1.getId()), t1, start);
        var otherOrder = newOrder(firmA, "NEW", serviceObject).getId();
        var draft = createDraft(otherOrder, company, List.of(t1.getId()), t1.getId());
        var proposalPath = orderPath(otherOrder) + "/visits/" + draft + "/proposals";
        visitWrite(post(proposalPath), coordinator, timeRange(start, start.plusHours(1)))
                .andExpect(status().isConflict());

        orderAction(orderId, "cancel", company).andExpect(status().isOk());
        assertThat(visits.findById(confirmed).orElseThrow().getStatus()).isEqualTo("CANCELLED");
        var proposal = propose(proposalPath, coordinator, start);
        mvc.perform(post(proposalPath + "/" + proposal + "/accept").session(ownerSession).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void orderCancellationAndStartingTheVisitCannotBothSucceed() throws Exception {
        var company = login(companyA);
        var coordinator = login(technicianLogin);
        var planned = nextTuesday(9);
        var visit = confirmVisit(company, List.of(t1.getId()), t1, planned);
        var visitPath = orderPath(orderId) + "/visits/" + visit;
        when(visitClock.instant()).thenReturn(planned.toInstant());
        mvc.perform(post(visitPath + "/cancel").session(company).with(csrf())).andExpect(status().isConflict());

        var codes = concurrently(
                () -> orderAction(orderId, "cancel", company).andReturn().getResponse().getStatus(),
                () -> mvc.perform(post(visitPath + "/start").session(coordinator).with(csrf()))
                        .andReturn().getResponse().getStatus());
        var stored = visits.findById(visit).orElseThrow();
        if (codes.getFirst() == 200) {
            assertThat(codes.getLast()).isIn(404, 409);
            assertThat(stored.getStatus()).isEqualTo("CANCELLED");
            assertThat(stored.getStartedAt()).isNull();
            assertThat(statusHistory()).endsWith("VISIT_CONFIRMED", "CANCELLED");
        } else {
            assertThat(codes).containsExactly(409, 200);
            assertThat(stored.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(stored.getStartedAt()).isNotNull();
            assertThat(statusHistory()).endsWith("VISIT_CONFIRMED", "IN_PROGRESS");
            orderAction(orderId, "cancel", company).andExpect(status().isConflict());
        }
    }

    private ResultActions orderAction(UUID id, String action, MockHttpSession session) throws Exception {
        return mvc.perform(post(orderPath(id) + "/" + action).session(session).with(csrf()));
    }

    @Test
    void orderClosingRequiresCompletedWorkAndTheOrdersFinalReport() throws Exception {
        var company = login(companyA);
        var ownerSession = login(owner);
        orderAction(orderId, "close", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        orderAction(orderId, "close", ownerSession).andExpect(status().isForbidden());
        orderAction(orderId, "close", login(companyB)).andExpect(status().isNotFound());

        var visitId = completeFirstVisit(company);
        orderAction(orderId, "close", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FINALIZED"));
        var report = createDraftReport();
        orderAction(orderId, "close", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FINALIZED"));
        mvc.perform(get(orderPath(orderId)).session(ownerSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reportFinalized").value(false));

        finalizeReport(report);
        mvc.perform(get(orderPath(orderId)).session(company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.reportFinalized").value(true));
        orderAction(orderId, "close", company).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isString())
                .andExpect(jsonPath("$.reportFinalized").value(true));
        orderAction(orderId, "close", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));
        assertThat(activeCount("order_technician_assignment")).isZero();
        assertThat(visits.findById(visitId).orElseThrow().getStatus()).isEqualTo("COMPLETED");
        assertThat(statusHistory()).endsWith("IN_PROGRESS", "COMPLETED", "CLOSED");
    }

    private UUID completeFirstVisit(MockHttpSession company) throws Exception {
        var coordinator = login(technicianLogin);
        var planned = nextTuesday(9);
        var visitId = confirmVisit(company, List.of(t1.getId()), t1, planned);
        var visitPath = orderPath(orderId) + "/visits/" + visitId;
        when(visitClock.instant()).thenReturn(planned.toInstant());
        mvc.perform(post(visitPath + "/start").session(coordinator).with(csrf())).andExpect(status().isOk());
        when(visitClock.instant()).thenReturn(planned.plusMinutes(30).toInstant());
        visitWrite(post(visitPath + "/complete"), coordinator, Map.of("outcome", "COMPLETED"))
                .andExpect(status().isOk());
        when(visitClock.instant()).thenReturn(planned.plusHours(1).toInstant());
        return visitId;
    }

    private ServiceReport createDraftReport() {
        var report = new ServiceReport();
        report.setServiceOrder(orders.findById(orderId).orElseThrow());
        report.setAuthorTechnician(t1);
        report.setCreatedAt(OffsetDateTime.now(visitClock));
        return reports.save(report);
    }

    private void finalizeReport(ServiceReport report) {
        report.setActionsDone("Weryfikacja działania");
        report.setDiagnosis("Uszkodzony filtr");
        report.setResolution("Wymieniono filtr");
        report.setFinalizedAt(OffsetDateTime.now(visitClock));
        reports.save(report);
    }

    @Test
    void visitCancellationHonorsStartBoundaryAndReleasesReservation() throws Exception {
        var company = login(companyA);
        var ownerSession = login(owner);
        var coordinator = login(technicianLogin);
        var id = createDraft(orderId, company, List.of(t1.getId()), t1.getId());
        var visitsPath = orderPath(orderId) + "/visits";
        var path = visitsPath + "/" + id;
        var start = nextTuesday(9);
        var range = timeRange(start, start.plusHours(1));
        setTuesdayHours(ownerSession, serviceObject.getLocation().getId(), "08:00", "16:00");
        var proposed = visitWrite(post(path + "/proposals"), coordinator, range).andExpect(status().isOk()).andReturn();
        var proposal = proposalId(proposed);
        mvc.perform(post(path + "/proposals/" + proposal + "/accept").session(ownerSession).with(csrf()))
                .andExpect(status().isOk());
        mvc.perform(post(path + "/cancel").session(ownerSession).with(csrf())).andExpect(status().isForbidden());

        when(visitClock.instant()).thenReturn(start.toInstant());
        mvc.perform(post(path + "/cancel").session(company).with(csrf())).andExpect(status().isConflict());
        assertThat(currentStatus()).isEqualTo("VISIT_CONFIRMED");

        when(visitClock.instant()).thenReturn(start.minusMinutes(1).toInstant());
        mvc.perform(post(path + "/cancel").session(company).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.startsAt").isString());
        mvc.perform(post(path + "/cancel").session(company).with(csrf())).andExpect(status().isConflict());
        assertThat(proposals.findById(proposal).orElseThrow().getStatus()).isEqualTo("ACCEPTED");
        assertThat(currentStatus()).isEqualTo("ASSIGNED");

        var newVisit = visitWrite(post(visitsPath), company,
                Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId()))
                .andExpect(status().isCreated()).andReturn();
        var newId = mapper.readTree(newVisit.getResponse().getContentAsString()).get("id").asText();
        assertThat(newId).isNotEqualTo(id.toString());
        var draftProposal = visitWrite(post(visitsPath + "/" + newId + "/proposals"), coordinator, range)
                .andExpect(status().isOk()).andReturn();
        mvc.perform(post(visitsPath + "/" + newId + "/cancel").session(company).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(proposals.findById(proposalId(draftProposal)).orElseThrow().getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void visitConcurrentAcceptancesAtDifferentLocationsReserveSharedPerformerOnce() throws Exception {
        var company = login(companyA);
        var ownerSessionA = login(owner);
        var ownerSessionB = login(owner);
        var otherCoordinatorAccount = account("TECHNICIAN", "ACTIVE");
        var otherCoordinator = technician(otherCoordinatorAccount, a1, "Drugi koordynator", true);
        var firstActor = login(t2.getUser());
        var secondActor = login(otherCoordinatorAccount);
        var otherObject = objectAtNewLocation(serviceObject.getLocation().getAssetOwner(), "Druga lokalizacja");
        var otherOrder = newOrder(firmA, "NEW", otherObject).getId();
        setTuesdayHours(ownerSessionA, serviceObject.getLocation().getId(), "08:00", "16:00");
        setTuesdayHours(ownerSessionB, otherObject.getLocation().getId(), "08:00", "16:00");
        var visitA = createDraft(orderId, company, List.of(t2.getId(), t1.getId()), t2.getId());
        var visitB = createDraft(otherOrder, company, List.of(otherCoordinator.getId(), t1.getId()), otherCoordinator.getId());
        var start = nextTuesday(9);
        var proposalPathA = orderPath(orderId) + "/visits/" + visitA + "/proposals";
        var proposalPathB = orderPath(otherOrder) + "/visits/" + visitB + "/proposals";
        var proposalA = propose(proposalPathA, firstActor, start);
        var proposalB = propose(proposalPathB, secondActor, start);

        var results = concurrently(
                () -> mvc.perform(post(proposalPathA + "/" + proposalA + "/accept").session(ownerSessionA).with(csrf())).andReturn(),
                () -> mvc.perform(post(proposalPathB + "/" + proposalB + "/accept").session(ownerSessionB).with(csrf())).andReturn());
        assertThat(results).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 409);
        var firstWon = results.getFirst().getResponse().getStatus() == 200;
        var conflict = results.get(firstWon ? 1 : 0).getResponse();
        assertThat(mapper.readTree(conflict.getContentAsString()).path("code").asText()).isEqualTo("TECHNICIAN_TIME_CONFLICT");
        var pendingId = firstWon ? proposalB : proposalA;
        var waiting = visits.findById(firstWon ? visitB : visitA).orElseThrow();
        assertThat(waiting.getStatus()).isEqualTo("DRAFT");
        assertThat(waiting.getStartsAt()).isNull();
        assertThat(proposals.findById(pendingId).orElseThrow().getRespondedAt()).isNull();

        var waitingPath = firstWon ? proposalPathB : proposalPathA;
        var waitingActor = firstWon ? secondActor : firstActor;
        mvc.perform(post(waitingPath + "/" + pendingId + "/reject").session(ownerSessionA).with(csrf()))
                .andExpect(status().isOk());
        visitWrite(post(waitingPath), waitingActor, timeRange(start, start.plusHours(1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TECHNICIAN_TIME_CONFLICT"));
        var touching = propose(waitingPath, waitingActor, start.plusHours(1));
        mvc.perform(post(waitingPath + "/" + touching + "/accept").session(ownerSessionA).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void visitConcurrentDraftCreationCommitsOnlyOneVisit() throws Exception {
        var first = login(companyA);
        var second = login(companyA);
        put(first, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        var visitsPath = orderPath(orderId) + "/visits";
        var staff = Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId());
        var codes = concurrently(
                () -> visitWrite(post(visitsPath), first, staff).andReturn().getResponse().getStatus(),
                () -> visitWrite(post(visitsPath), second, staff).andReturn().getResponse().getStatus());
        assertThat(codes).containsExactlyInAnyOrder(201, 409);
        assertThat(visits.findAllByServiceOrderIdOrderByCreatedAtDescIdAsc(orderId)).hasSize(1);
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING");
    }

    private UUID propose(String path, MockHttpSession coordinator, OffsetDateTime start) throws Exception {
        return proposalId(visitWrite(post(path), coordinator, timeRange(start, start.plusHours(1)))
                .andExpect(status().isOk()).andReturn());
    }

    @Test
    void visitConcurrentAvailabilityEditAndAcceptanceKeepAConsistentResult() throws Exception {
        var ownerEditing = login(owner);
        var ownerAccepting = login(owner);
        var coordinator = login(technicianLogin);
        var locationId = serviceObject.getLocation().getId();
        setTuesdayHours(ownerEditing, locationId, "08:00", "16:00");
        var visit = createDraft(orderId, login(companyA), List.of(t1.getId()), t1.getId());
        var path = orderPath(orderId) + "/visits/" + visit + "/proposals";
        var proposal = propose(path, coordinator, nextTuesday(9));

        var results = concurrently(
                () -> {
                    setTuesdayHours(ownerEditing, locationId, "12:00", "16:00");
                    return "updated";
                },
                () -> {
                    var response = mvc.perform(post(path + "/" + proposal + "/accept").session(ownerAccepting).with(csrf()))
                            .andReturn().getResponse();
                    if (response.getStatus() == 200) return "confirmed";
                    assertThat(response.getStatus()).isEqualTo(409);
                    assertThat(mapper.readTree(response.getContentAsString()).path("code").asText())
                            .isEqualTo("OUTSIDE_LOCATION_AVAILABILITY");
                    return "pending";
                });
        var confirmed = results.getLast().equals("confirmed");
        assertThat(proposals.findById(proposal).orElseThrow().getStatus()).isEqualTo(confirmed ? "ACCEPTED" : "PENDING");
        assertThat(currentStatus()).isEqualTo(confirmed ? "VISIT_CONFIRMED" : "AWAITING_SCHEDULING");
        if (confirmed) {
            assertThat(visits.findById(visit).orElseThrow().getStartsAt()).isNotNull();
        } else {
            assertThat(visits.findById(visit).orElseThrow().getStartsAt()).isNull();
        }
    }

    @Test
    void visitConcurrentAcceptancesAllowIndependentStaffAndKeepOneResponsePerProposal() throws Exception {
        var company = login(companyA);
        var ownerA = login(owner);
        var ownerB = login(owner);
        var technicianA = login(technicianLogin);
        var technicianB = login(t2.getUser());
        setTuesdayHours(ownerA, serviceObject.getLocation().getId(), "08:00", "16:00");
        var otherOrder = newOrder(firmA, "NEW", serviceObject).getId();
        var visitA = createDraft(orderId, company, List.of(t1.getId()), t1.getId());
        var visitB = createDraft(otherOrder, company, List.of(t2.getId()), t2.getId());
        var pathA = orderPath(orderId) + "/visits/" + visitA + "/proposals";
        var pathB = orderPath(otherOrder) + "/visits/" + visitB + "/proposals";
        var startsAt = nextTuesday(9);
        var proposalA = propose(pathA, technicianA, startsAt);
        var initialB = propose(pathB, technicianB, startsAt);
        var counterB = visitWrite(post(pathB + "/" + initialB + "/counter"), ownerB,
                timeRange(startsAt, startsAt.plusHours(1))).andExpect(status().isOk()).andReturn();
        var proposalB = proposalId(counterB);

        var acceptA = pathA + "/" + proposalA + "/accept";
        var acceptB = pathB + "/" + proposalB + "/accept";
        assertThat(concurrently(() -> respond(acceptA, ownerA), () -> respond(acceptB, technicianB)))
                .containsExactly(200, 200);

        var thirdOrder = newOrder(firmA, "NEW", serviceObject).getId();
        var visitC = createDraft(thirdOrder, company, List.of(t1.getId()), t1.getId());
        var pathC = orderPath(thirdOrder) + "/visits/" + visitC + "/proposals";
        var proposalC = propose(pathC, technicianA, startsAt.plusHours(1));
        var acceptC = pathC + "/" + proposalC + "/accept";
        assertThat(concurrently(() -> respond(acceptC, ownerA), () -> respond(acceptC, ownerB)))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(proposals.findById(proposalC).orElseThrow().getStatus()).isEqualTo("ACCEPTED");
    }

    private int respond(String path, MockHttpSession client) throws Exception {
        return mvc.perform(post(path).session(client).with(csrf())).andReturn().getResponse().getStatus();
    }

    @Test
    void getReturnsEmptyAssignmentForNewOrder() throws Exception {
        mvc.perform(get(path(orderId)).session(login(companyA))).andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceOrderId").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.branches").isEmpty())
                .andExpect(jsonPath("$.technicians").isEmpty())
                .andExpect(jsonPath("$.coordinatorTechnicianId").doesNotExist());
    }

    @Test
    void firstSaveAssignsWholeBranchAndMovesStatusToAssigned() throws Exception {
        var session = login(companyA);
        put(session, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.branches[0].id").value(a1.getId().toString()))
                .andExpect(jsonPath("$.branches[0].name").value("A1 Warszawa"))
                .andExpect(jsonPath("$.technicians").isEmpty())
                .andExpect(jsonPath("$.coordinatorTechnicianId").value(t1.getId().toString()));
        assertThat(statusHistory()).containsExactly("ASSIGNED");
        assertThat(currentStatus()).isEqualTo("ASSIGNED");
        mvc.perform(get(path(orderId)).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.branches.length()").value(1));
    }

    @Test
    void savesTechniciansFromDifferentBranchesAndMixedSelection() throws Exception {
        var session = login(companyA);
        put(session, body(0, List.of(), List.of(t1.getId(), t2.getId()), t2.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.technicians.length()").value(2))
                .andExpect(jsonPath("$.technicians[0].displayName").value("Anna A1"))
                .andExpect(jsonPath("$.technicians[0].branchId").value(a1.getId().toString()))
                .andExpect(jsonPath("$.technicians[1].displayName").value("Bartek A2"));
        put(session, body(1, List.of(a1.getId()), List.of(t2.getId()), t1.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.branches.length()").value(1))
                .andExpect(jsonPath("$.technicians.length()").value(1))
                .andExpect(jsonPath("$.technicians[0].id").value(t2.getId().toString()));
        assertThat(statusHistory()).containsExactly("ASSIGNED");
    }

    @Test
    void identicalSaveChangesNothing() throws Exception {
        var session = login(companyA);
        var data = body(0, List.of(a1.getId()), List.of(t2.getId()), t2.getId());
        put(session, data).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        var again = body(1, List.of(a1.getId()), List.of(t2.getId()), t2.getId());
        put(session, again).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        assertThat(count("order_branch_assignment")).isEqualTo(1);
        assertThat(count("order_technician_assignment")).isEqualTo(1);
        assertThat(statusHistory()).containsExactly("ASSIGNED");
    }

    @Test
    void changingSetEndsRemovedAssignmentsWithoutDeletingThem() throws Exception {
        var session = login(companyA);
        put(session, body(0, List.of(a1.getId()), List.of(t2.getId()), t1.getId())).andExpect(status().isOk());
        put(session, body(1, List.of(a2.getId()), List.of(), t2.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.branches[0].id").value(a2.getId().toString()))
                .andExpect(jsonPath("$.technicians").isEmpty())
                .andExpect(jsonPath("$.coordinatorTechnicianId").value(t2.getId().toString()));
        assertThat(count("order_branch_assignment")).isEqualTo(2);
        assertThat(activeCount("order_branch_assignment")).isEqualTo(1);
        assertThat(count("order_technician_assignment")).isEqualTo(1);
        assertThat(activeCount("order_technician_assignment")).isZero();
        // Ponowne dodanie zdjętego oddziału tworzy nowy wiersz, stary zostaje w historii.
        put(session, body(2, List.of(a1.getId(), a2.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        assertThat(count("order_branch_assignment")).isEqualTo(3);
        assertThat(activeCount("order_branch_assignment")).isEqualTo(2);
    }

    @Test
    void changingOnlyCoordinatorBumpsVersionWithoutStatusHistory() throws Exception {
        var session = login(companyA);
        put(session, body(0, List.of(a1.getId(), a2.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        put(session, body(1, List.of(a1.getId(), a2.getId()), List.of(), t2.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.coordinatorTechnicianId").value(t2.getId().toString()));
        assertThat(count("order_branch_assignment")).isEqualTo(2);
        assertThat(statusHistory()).containsExactly("ASSIGNED");
    }

    @Test
    void technicianListFiltersBeforePagingAndDeduplicatesMixedAssignments() throws Exception {
        var firmSession = login(companyA);
        var technicianSession = login(technicianLogin);
        var directOrderId = newOrder(firmA, "NEW", serviceObject).getId();
        var assignedToOther = newOrder(firmA, "NEW", serviceObject).getId();
        var unassigned = newOrder(firmA, "NEW", serviceObject).getId();
        var teammate = account("TECHNICIAN", "ACTIVE");
        var colleague = technician(teammate, a1, "Kolega z oddziału A1", true);

        put(firmSession, body(0, List.of(a1.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        put(firmSession, body(0, List.of(), List.of(t1.getId()), t1.getId()), directOrderId).andExpect(status().isOk());
        put(firmSession, body(0, List.of(), List.of(colleague.getId()), colleague.getId()), assignedToOther)
                .andExpect(status().isOk());
        jdbc.update("update service_order set created_at = ? where id in (?, ?)", now(), orderId, directOrderId);
        jdbc.update("update service_order set current_status = 'AWAITING_TECHNICIAN_REVIEW' where id = ?", directOrderId);
        jdbc.update("update service_order set created_at = ? where id = ?", now().plusMinutes(1), assignedToOther);

        var visible = List.of(orderId.toString(), directOrderId.toString()).stream().sorted().toList();
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(visible.toArray(String[]::new))));
        for (int page = 0; page < visible.size(); page++) {
            mvc.perform(get(ORDERS_PATH).session(technicianSession).param("size", "1")
                            .param("page", Integer.toString(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains(visible.get(page))));
        }
        mvc.perform(get(ORDERS_PATH).session(technicianSession).param("size", "1").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(ORDERS_PATH).session(technicianSession).param("status", "ASSIGNED").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains(orderId.toString())))
                .andExpect(jsonPath("$[0].serviceObjectName").value("Klimatyzator"))
                .andExpect(jsonPath("$[0].locationName").value("Lokalizacja"));
        mvc.perform(get(ORDERS_PATH).session(login(teammate))).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(orderId.toString(), assignedToOther.toString())));
        mvc.perform(get(ORDERS_PATH).session(firmSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(unassigned.toString())));
    }

    @Test
    void technicianListUsesCurrentBranchAndIgnoresEndedAssignments() throws Exception {
        var firmSession = login(companyA);
        var technicianSession = login(technicianLogin);
        var directOrderId = newOrder(firmA, "NEW", serviceObject).getId();
        put(firmSession, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        put(firmSession, body(0, List.of(), List.of(t1.getId()), t1.getId()), directOrderId).andExpect(status().isOk());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get(orderPath(orderId)).session(technicianSession)).andExpect(status().isOk());

        mvc.perform(putRequest("/api/technicians/" + t1.getId(),
                Map.of("branchId", a2.getId(), "displayName", t1.getDisplayName(), "active", true))
                .session(firmSession).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(directOrderId.toString())));
        mvc.perform(get(orderPath(orderId)).session(technicianSession)).andExpect(status().isNotFound());
        mvc.perform(get(orderPath(directOrderId)).session(technicianSession)).andExpect(status().isOk());

        put(firmSession, body(1, List.of(), List.of(t2.getId()), t2.getId()), directOrderId).andExpect(status().isOk());
        put(firmSession, body(1, List.of(a2.getId()), List.of(), t2.getId())).andExpect(status().isOk());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(orderId.toString())));
        mvc.perform(get(orderPath(orderId)).session(technicianSession)).andExpect(status().isOk());
        mvc.perform(get(orderPath(directOrderId)).session(technicianSession)).andExpect(status().isNotFound());
    }

    @Test
    void technicianListChecksCurrentActivityAndClosedOrders() throws Exception {
        var firmSession = login(companyA);
        var technicianSession = login(technicianLogin);
        put(firmSession, body(0, List.of(), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        var closedOrderId = newOrder(firmA, "NEW", serviceObject).getId();
        put(firmSession, body(0, List.of(), List.of(t1.getId()), t1.getId()), closedOrderId).andExpect(status().isOk());
        jdbc.update("update service_order set closed_at = ? where id = ?", now(), closedOrderId);
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(orderId.toString())));
        mvc.perform(get(orderPath(closedOrderId)).session(technicianSession)).andExpect(status().isNotFound());

        jdbc.update("update technician set active = false where id = ?", t1.getId());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(orderPath(orderId)).session(technicianSession)).andExpect(status().isNotFound());
        jdbc.update("update technician set active = true where id = ?", t1.getId());
        jdbc.update("update service_branch set active = false where id = ?", a1.getId());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mvc.perform(get(orderPath(orderId)).session(technicianSession)).andExpect(status().isNotFound());
        jdbc.update("update service_branch set active = true where id = ?", a1.getId());
        jdbc.update("update user_account set status = 'BLOCKED' where id = ?", technicianLogin.getId());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isUnauthorized());
    }

    @Test
    void technicianDetailsShowWorkLocationAndCoordinator() throws Exception {
        var firmSession = login(companyA);
        var technicianSession = login(technicianLogin);
        var colleagueAccount = account("TECHNICIAN", "ACTIVE");
        technician(colleagueAccount, a1, "Kolega z oddziału A1", true);
        var colleagueSession = login(colleagueAccount);
        var detailPath = orderPath(orderId);

        mvc.perform(get(detailPath).session(technicianSession)).andExpect(status().isNotFound());
        put(firmSession, body(0, List.of(), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        mvc.perform(get(detailPath).session(colleagueSession)).andExpect(status().isNotFound());
        mvc.perform(get(detailPath).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.serviceObjectId").value(serviceObject.getId().toString()))
                .andExpect(jsonPath("$.serviceObjectName").value("Klimatyzator"))
                .andExpect(jsonPath("$.locationId").isString())
                .andExpect(jsonPath("$.locationName").value("Lokalizacja"))
                .andExpect(jsonPath("$.addressLine").value("Testowa 1"))
                .andExpect(jsonPath("$.city").value("Warszawa"))
                .andExpect(jsonPath("$.timezone").value("Europe/Warsaw"))
                .andExpect(jsonPath("$.description").value("Nie chłodzi"))
                .andExpect(jsonPath("$.currentStatus").value("ASSIGNED"))
                .andExpect(jsonPath("$.coordinator.id").value(t1.getId().toString()))
                .andExpect(jsonPath("$.coordinator.displayName").value(t1.getDisplayName()))
                .andExpect(jsonPath("$.assetOwnerId").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.createdByUserId").doesNotHaveJsonPath());

        put(firmSession, body(1, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        mvc.perform(get(detailPath).session(colleagueSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinator.id").value(t1.getId().toString()));
        jdbc.update("update service_order set coordinator_technician_id = null where id = ?", orderId);
        mvc.perform(get(detailPath).session(colleagueSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinator").value(nullValue()));
    }

    @Test
    void technicianDetailsAndListDoNotExposeOrdersOfAnotherCompany() throws Exception {
        var technicianSession = login(technicianLogin);
        var otherOrderId = newOrder(firmB, "NEW", serviceObject).getId();
        put(login(companyB), body(0, List.of(b1.getId()), List.of(tb1.getId()), tb1.getId()), otherOrderId)
                .andExpect(status().isOk());
        mvc.perform(get(orderPath(otherOrderId)).session(login(tb1.getUser()))).andExpect(status().isOk());
        mvc.perform(get(orderPath(otherOrderId)).session(technicianSession)).andExpect(status().isNotFound());
        mvc.perform(get(orderPath(UUID.randomUUID())).session(technicianSession)).andExpect(status().isNotFound());
        mvc.perform(get(ORDERS_PATH).session(technicianSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void technicianReadPermissionsLeaveOtherOrderAndOwnerEndpointsProtected() throws Exception {
        var firmSession = login(companyA);
        var ownerSession = login(owner);
        var technicianSession = login(technicianLogin);
        var detailPath = orderPath(orderId);
        var assignment = body(0, List.of(a1.getId()), List.of(), t1.getId());
        put(firmSession, assignment).andExpect(status().isOk());

        mvc.perform(get(ORDERS_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(detailPath)).andExpect(status().isUnauthorized());
        mvc.perform(get(detailPath).session(login(companyB))).andExpect(status().isNotFound());
        for (var session : List.of(ownerSession, firmSession)) {
            mvc.perform(get(ORDERS_PATH).session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].id", contains(orderId.toString())));
            mvc.perform(get(detailPath).session(session)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.assetOwnerName").value("Właściciel"))
                    .andExpect(jsonPath("$.createdByUserId").value(owner.getId().toString()))
                    .andExpect(jsonPath("$.coordinator").doesNotHaveJsonPath());
        }
        mvc.perform(post(ORDERS_PATH).session(technicianSession).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(path(orderId)).session(technicianSession)).andExpect(status().isForbidden());
        put(technicianSession, assignment).andExpect(status().isForbidden());
        mvc.perform(get("/api/service-objects/" + serviceObject.getId()).session(technicianSession))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/locations/" + serviceObject.getLocation().getId()).session(technicianSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidSelectionsWithoutAnyWrite() throws Exception {
        var session = login(companyA);
        var cases = List.of(
                Map.entry("branchIds", body(0, List.of(), List.of(), t1.getId())),
                Map.entry("branchIds", body(0, List.of(a1.getId(), a1.getId()), List.of(), t1.getId())),
                Map.entry("branchIds", body(0, List.of(aInactive.getId()), List.of(t1.getId()), t1.getId())),
                Map.entry("branchIds", body(0, List.of(b1.getId()), List.of(t1.getId()), t1.getId())),
                Map.entry("technicianIds", body(0, List.of(), List.of(tInactive.getId()), t1.getId())),
                Map.entry("technicianIds", body(0, List.of(), List.of(tBlockedAccount.getId()), t1.getId())),
                Map.entry("technicianIds", body(0, List.of(), List.of(tb1.getId()), t1.getId())),
                Map.entry("technicianIds", body(0, List.of(), List.of(t1.getId(), t1.getId()), t1.getId())),
                Map.entry("coordinatorTechnicianId", body(0, List.of(a1.getId()), List.of(), t2.getId())),
                Map.entry("coordinatorTechnicianId", body(0, List.of(a1.getId()), List.of(), tb1.getId())),
                Map.entry("coordinatorTechnicianId", body(0, List.of(a1.getId()), List.of(), tInactive.getId())));
        for (var entry : cases) {
            put(session, entry.getValue()).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors." + entry.getKey()).isArray());
        }
        var missing = new HashMap<String, Object>(body(0, List.of(a1.getId()), List.of(), t1.getId()));
        missing.remove("coordinatorTechnicianId");
        put(session, missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coordinatorTechnicianId").isArray());
        assertNothingWritten();
    }

    @Test
    void onlyOwningCompanyCanReadOrChangeAssignment() throws Exception {
        var data = body(0, List.of(a1.getId()), List.of(), t1.getId());
        mvc.perform(get(path(orderId))).andExpect(status().isUnauthorized());
        mvc.perform(putRequest(path(orderId), data).with(csrf())).andExpect(status().isUnauthorized());
        for (var account : List.of(owner, technicianLogin)) {
            var session = login(account);
            mvc.perform(get(path(orderId)).session(session)).andExpect(status().isForbidden());
            put(session, data).andExpect(status().isForbidden());
        }
        var other = login(companyB);
        mvc.perform(get(path(orderId)).session(other)).andExpect(status().isNotFound());
        put(other, data).andExpect(status().isNotFound());
        mvc.perform(putRequest(path(orderId), data).session(login(companyA))).andExpect(status().isForbidden());
        put(login(companyA), body(0, List.of(a1.getId()), List.of(), t1.getId()), UUID.randomUUID())
                .andExpect(status().isNotFound());
        assertNothingWritten();
    }

    @Test
    void staleVersionIsRejected() throws Exception {
        var session = login(companyA);
        put(session, body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        put(session, body(0, List.of(a2.getId()), List.of(), t2.getId())).andExpect(status().isConflict());
        mvc.perform(get(path(orderId)).session(session))
                .andExpect(jsonPath("$.branches[0].id").value(a1.getId().toString()));
    }

    @Test
    void orderOutsideNewOrAssignedCannotBeChanged() throws Exception {
        jdbc.update("update service_order set current_status = 'AWAITING_OWNER_CONFIRMATION' where id = ?", orderId);
        put(login(companyA), body(0, List.of(a1.getId()), List.of(), t1.getId())).andExpect(status().isConflict());
        assertNothingWritten();
    }

    @Test
    void concurrentSavesWithSameVersionLetOnlyOneWin() throws Exception {
        var first = login(companyA);
        var second = login(companyA);
        var codes = concurrently(
                () -> put(first, body(0, List.of(a1.getId()), List.of(), t1.getId())).andReturn().getResponse().getStatus(),
                () -> put(second, body(0, List.of(a2.getId()), List.of(), t2.getId())).andReturn().getResponse().getStatus());
        assertThat(codes).containsExactlyInAnyOrder(200, 409);
        assertThat(activeCount("order_branch_assignment")).isEqualTo(1);
        assertThat(statusHistory()).containsExactly("ASSIGNED");
        assertThat(jdbc.queryForObject("select assignment_version from service_order where id = ?", Long.class, orderId))
                .isEqualTo(1L);
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
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

    private void assertNothingWritten() {
        assertThat(count("order_branch_assignment")).isZero();
        assertThat(count("order_technician_assignment")).isZero();
        assertThat(statusHistory()).isEmpty();
        assertThat(currentStatus()).isNotEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("select assignment_version from service_order where id = ?", Long.class, orderId))
                .isZero();
    }

    private ResultActions put(MockHttpSession session, Map<String, Object> data) throws Exception {
        return put(session, data, orderId);
    }

    private ResultActions put(MockHttpSession session, Map<String, Object> data, UUID id) throws Exception {
        return mvc.perform(putRequest(path(id), data).session(session).with(csrf()));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder putRequest(
            String url, Map<String, Object> data) throws Exception {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url)
                .contentType("application/json").content(mapper.writeValueAsString(data));
    }

    private static Map<String, Object> body(long version, List<UUID> branchIds, List<UUID> technicianIds,
                                            UUID coordinator) {
        var data = new HashMap<String, Object>();
        data.put("expectedVersion", version);
        data.put("branchIds", branchIds);
        data.put("technicianIds", technicianIds);
        data.put("coordinatorTechnicianId", coordinator);
        return data;
    }

    private static String path(UUID id) {
        return orderPath(id) + "/assignment";
    }

    private static String orderPath(UUID id) {
        return ORDERS_PATH + "/" + id;
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where service_order_id = ?", Integer.class, orderId);
    }

    private int activeCount(String table) {
        return jdbc.queryForObject("select count(*) from " + table
                + " where service_order_id = ? and unassigned_at is null", Integer.class, orderId);
    }

    private List<String> statusHistory() {
        return jdbc.queryForList("select status from order_status_history where service_order_id = ? order by changed_at",
                String.class, orderId);
    }

    private String currentStatus() {
        return jdbc.queryForObject("select current_status from service_order where id = ?", String.class, orderId);
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        var result = mvc.perform(post("/login").with(csrf()).header("Accept", "application/json")
                        .param("username", account.getEmail()).param("password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private UserAccount account(String role, String status) {
        var account = new UserAccount();
        account.setEmail(UUID.randomUUID() + "@example.com");
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setRole(role);
        account.setStatus(status);
        account.setCreatedAt(now());
        return accounts.save(account);
    }

    private ServiceCompany company(UserAccount account) {
        var company = new ServiceCompany();
        company.setUser(account);
        company.setName("Firma " + account.getEmail());
        company.setCreatedAt(now());
        return companies.save(company);
    }

    private ServiceBranch branch(ServiceCompany company, String name, boolean active) {
        var branch = new ServiceBranch();
        branch.setServiceCompany(company);
        branch.setName(name);
        branch.setActive(active);
        branch.setCreatedAt(now());
        return branches.save(branch);
    }

    private Technician technician(UserAccount account, ServiceBranch branch, String name, boolean active) {
        var technician = new Technician();
        technician.setUser(account);
        technician.setBranch(branch);
        technician.setDisplayName(name);
        technician.setActive(active);
        technician.setCreatedAt(now());
        return technicians.save(technician);
    }

    private ServiceOrder order(ServiceCompany company, String status) {
        var assetOwner = new AssetOwner();
        assetOwner.setUser(owner);
        assetOwner.setName("Właściciel");
        assetOwner.setCreatedAt(now());
        owners.save(assetOwner);
        return newOrder(company, status, objectAtNewLocation(assetOwner, "Lokalizacja"));
    }

    private ServiceObject objectAtNewLocation(AssetOwner assetOwner, String name) {
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

    private ServiceOrder newOrder(ServiceCompany company, String status, ServiceObject object) {
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

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
