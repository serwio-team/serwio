package serwio.order;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.support.TransactionTemplate;
import serwio.report.model.ServiceReport;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderTerminationTest extends OrderApiFixture {

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
        mvc.perform(get(orderPath(orderId)).session(login(technicianLogin))).andExpect(status().isOk());
    }

    @Test
    void orderHistoricalAccessUsesAssignmentsAndBranchMembershipAtTermination() throws Exception {
        var company = login(companyA);
        var departure = technician(account("TECHNICIAN", "ACTIVE"), a1, "Serwisant przeniesiony", true);
        var individual = technician(account("TECHNICIAN", "ACTIVE"), a2, "Serwisant przypisany osobiście", true);
        var removed = technician(account("TECHNICIAN", "ACTIVE"), a2, "Serwisant odpięty", true);
        var t1Session = login(technicianLogin);
        var t2Session = login(t2.getUser());
        var departureSession = login(departure.getUser());
        var individualSession = login(individual.getUser());
        var removedSession = login(removed.getUser());
        var detail = orderPath(orderId);
        put(company, body(0, List.of(a1.getId()), List.of(individual.getId(), removed.getId()), t1.getId()))
                .andExpect(status().isOk());
        postDraft(orderId, company, List.of(t1.getId()), t1.getId());
        moveTechnician(company, departure, a2);
        put(company, body(1, List.of(a1.getId()), List.of(individual.getId()), t1.getId())).andExpect(status().isOk());
        orderAction(orderId, "cancel", company).andExpect(status().isOk());
        moveTechnician(company, t1, a2);
        moveTechnician(company, t2, a1);

        for (var authorized : List.of(t1Session, individualSession)) {
            mvc.perform(get(detail).session(authorized)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.currentStatus").value("CANCELLED"));
            mvc.perform(get(detail + "/visits").session(authorized)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.visits[0].status").value("CANCELLED"));
            mvc.perform(get(ORDERS_PATH).param("status", "CANCELLED").session(authorized))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", contains(orderId.toString())));
        }
        for (var unauthorized : List.of(t2Session, departureSession, removedSession)) {
            mvc.perform(get(detail).session(unauthorized)).andExpect(status().isNotFound());
            mvc.perform(get(detail + "/visits").session(unauthorized)).andExpect(status().isNotFound());
            mvc.perform(get(ORDERS_PATH).session(unauthorized)).andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    @Test
    void orderClosingRaceUsesCommittedReportAndAppendsOneTerminalHistoryEntry() throws Exception {
        var company = login(companyA);
        completeFirstVisit(company);
        var draft = createDraftReport();
        var codes = concurrently(
                () -> orderAction(orderId, "close", company).andReturn().getResponse().getStatus(),
                () -> new TransactionTemplate(transactionManager).execute(status -> {
                    // Finalization and closing share the order lock.
                    orders.findForUpdateByIdAndServiceCompanyUserId(orderId, companyA.getId()).orElseThrow();
                    finalizeReport(draft);
                    return 200;
                }));
        assertThat(codes.getLast()).isEqualTo(200);
        assertThat(reports.existsByServiceOrderIdAndFinalizedAtIsNotNull(orderId)).isTrue();
        if (codes.getFirst() == 409) {
            assertThat(currentStatus()).isEqualTo("COMPLETED");
            orderAction(orderId, "close", company).andExpect(status().isOk());
        } else {
            assertThat(codes.getFirst()).isEqualTo(200);
        }
        assertThat(currentStatus()).isEqualTo("CLOSED");
        assertThat(statusHistory()).containsExactly("ASSIGNED", "AWAITING_SCHEDULING", "VISIT_CONFIRMED",
                "IN_PROGRESS", "COMPLETED", "CLOSED");
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
}
