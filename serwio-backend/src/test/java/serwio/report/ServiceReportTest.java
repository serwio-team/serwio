package serwio.report;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import serwio.report.model.ServiceReport;
import serwio.technician.model.Technician;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceReportTest extends OrderApiFixture {

    @Test
    void reportDraftHasOneAuthorAndSupportsResumingAnIncompleteReport() throws Exception {
        var company = login(companyA);
        var ownerSession = login(owner);
        var author = login(technicianLogin);
        var otherTechnician = login(t2.getUser());
        var path = reportPath(orderId);
        put(company, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).session(author)).andExpect(status().isNotFound());
        mvc.perform(putRequest(path, Map.of()).session(author)).andExpect(status().isForbidden());
        putReport(ownerSession, Map.of()).andExpect(status().isForbidden());
        putReport(company, Map.of()).andExpect(status().isForbidden());
        putReport(otherTechnician, Map.of()).andExpect(status().isForbidden());
        putReport(author, Map.of("actionsDone", "a".repeat(ServiceReport.MAX_TEXT_LENGTH + 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.actionsDone").isArray());
        assertThat(reports.findByServiceOrderId(orderId)).isEmpty();

        var created = putReport(author, Map.of("actionsDone", "  Oczyszczono filtr.  ", "diagnosis", "  "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authorTechnicianId").value(t1.getId().toString()))
                .andExpect(jsonPath("$.serviceOrderId").value(orderId.toString()))
                .andExpect(jsonPath("$.actionsDone").value("Oczyszczono filtr."))
                .andExpect(jsonPath("$.diagnosis").value(nullValue()))
                .andExpect(jsonPath("$.finalizedAt").value(nullValue())).andReturn();
        var reportId = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();
        for (var hidden : List.of(company, ownerSession, otherTechnician, login(companyB))) {
            mvc.perform(get(path).session(hidden)).andExpect(status().isNotFound());
        }
        mvc.perform(get(path).session(author)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.actionsDone").value("Oczyszczono filtr."));

        putReport(author, Map.of("diagnosis", "Zużyty filtr", "usedMaterials", "Nowy filtr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.actionsDone").value(nullValue()))
                .andExpect(jsonPath("$.diagnosis").value("Zużyty filtr"))
                .andExpect(jsonPath("$.usedMaterials").value("Nowy filtr"));
        assertThat(count("service_report")).isEqualTo(1);
    }

    @Test
    void reportDraftAuthorDoesNotChangeWithTheOrderCoordinatorAndStillNeedsCurrentAccess() throws Exception {
        var company = login(companyA);
        var author = login(technicianLogin);
        var newCoordinator = login(t2.getUser());
        put(company, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        putReport(author, Map.of("actionsDone", "Próba działania")).andExpect(status().isOk());
        put(company, body(1, List.of(a2.getId()), List.of(t1.getId()), t2.getId())).andExpect(status().isOk());
        putReport(newCoordinator, Map.of()).andExpect(status().isForbidden());
        putReport(author, Map.of("actionsDone", "Powtórzono próbę")).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorTechnicianId").value(t1.getId().toString()));

        put(company, body(2, List.of(a2.getId()), List.of(), t2.getId())).andExpect(status().isOk());
        putReport(author, Map.of()).andExpect(status().isNotFound());
        mvc.perform(get(reportPath(orderId)).session(author)).andExpect(status().isNotFound());
        assertThat(reports.findByServiceOrderId(orderId).orElseThrow().getActionsDone()).isEqualTo("Powtórzono próbę");
    }

    @Test
    void reportFinalizationValidatesFieldsThenEnablesClosingAndHistoricalRead() throws Exception {
        var company = login(companyA);
        var ownerSession = login(owner);
        var author = login(technicianLogin);
        var colleague = login(t2.getUser());
        var outsider = technician(account("TECHNICIAN", "ACTIVE"), a1, "Nowy członek oddziału A2", true);
        var outsiderSession = login(outsider.getUser());
        var path = reportPath(orderId);
        var visitId = confirmVisit(company, List.of(t1.getId()), t1, nextTuesday(9));
        recordVisitResult(orderId, visitId, author, "COMPLETED");

        orderAction(orderId, "close", company).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FINALIZED"));
        finalizeReportRequest(author).andExpect(status().isNotFound());
        putReport(author, Map.of("actionsDone", "  ", "diagnosis", "  ", "resolution", "\n"))
                .andExpect(status().isOk());
        finalizeReportRequest(author).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.actionsDone").isArray())
                .andExpect(jsonPath("$.errors.diagnosis").isArray())
                .andExpect(jsonPath("$.errors.resolution").isArray());
        assertThat(reports.existsByServiceOrderIdAndFinalizedAtIsNotNull(orderId)).isFalse();
        putReport(author, validReport()).andExpect(status().isOk());
        finalizeReportRequest(colleague).andExpect(status().isForbidden());
        finalizeReportRequest(ownerSession).andExpect(status().isForbidden());
        mvc.perform(post(path + "/finalize").session(author)).andExpect(status().isForbidden());
        finalizeReportRequest(author).andExpect(status().isOk()).andExpect(jsonPath("$.finalizedAt").isString());
        var finalized = reports.findByServiceOrderId(orderId).orElseThrow().getFinalizedAt();
        finalizeReportRequest(author).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_FINALIZED"));
        putReport(author, Map.of("actionsDone", "Zmieniono"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REPORT_ALREADY_FINALIZED"));
        assertThat(reports.findByServiceOrderId(orderId).orElseThrow().getFinalizedAt()).isEqualTo(finalized);

        mvc.perform(get(orderPath(orderId)).session(company)).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.reportFinalized").value(true));
        mvc.perform(get(path).session(ownerSession)).andExpect(status().isOk());
        mvc.perform(get(path).session(login(companyB))).andExpect(status().isNotFound());
        mvc.perform(get(path).session(login(tb1.getUser()))).andExpect(status().isNotFound());
        orderAction(orderId, "close", company).andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("CLOSED"));
        moveTechnician(company, t2, a1);
        moveTechnician(company, outsider, a2);
        for (var reader : List.of(company, ownerSession, author, colleague)) {
            mvc.perform(get(path).session(reader)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.actionsDone").value(validReport().get("actionsDone")))
                    .andExpect(jsonPath("$.finalizedAt").isString());
        }
        mvc.perform(get(path).session(outsiderSession)).andExpect(status().isNotFound());
    }

    @Test
    void reportFinalizationRequiresCompletedOrderAndCompletedVisitOnItsOwnOrder() throws Exception {
        var company = login(companyA);
        var author = login(technicianLogin);
        var coordinator = login(t2.getUser());
        put(company, body(0, List.of(a2.getId()), List.of(t1.getId()), t1.getId())).andExpect(status().isOk());
        putReport(author, validReport()).andExpect(status().isOk());
        finalizeReportRequest(author).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_STATE"));

        var cancelled = postDraft(orderId, company, List.of(t1.getId()), t1.getId());
        var visitsPath = orderPath(orderId) + "/visits/";
        mvc.perform(post(visitsPath + cancelled + "/cancel").session(company).with(csrf()))
                .andExpect(status().isOk());
        var planned = nextTuesday(9);
        var completed = postDraft(orderId, company, List.of(t2.getId()), t2.getId());
        confirmDraft(completed, t2, planned);
        var otherOrder = newOrder(firmA, "NEW", serviceObject).getId();
        var otherVisit = createDraft(otherOrder, company, List.of(t1.getId()), t1.getId());
        var otherProposalPath = orderPath(otherOrder) + "/visits/" + otherVisit + "/proposals";
        var otherProposal = propose(otherProposalPath, author, planned.plusHours(1));
        mvc.perform(post(otherProposalPath + "/" + otherProposal + "/accept").session(login(owner)).with(csrf()))
                .andExpect(status().isOk());
        recordVisitResult(otherOrder, otherVisit, author, "COMPLETED");
        recordVisitResult(orderId, completed, coordinator, "COMPLETED");
        finalizeReportRequest(author).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_AUTHOR_NOT_PARTICIPANT"));
        assertThat(reports.existsByServiceOrderIdAndFinalizedAtIsNotNull(orderId)).isFalse();
    }

    @Test
    void reportAuthorChangePreservesTheDraftAndRecordsWhoChangedIt() throws Exception {
        var company = login(companyA);
        var previous = login(technicianLogin);
        var selected = login(t2.getUser());
        var planned = nextTuesday(9);
        var visit = confirmVisit(company, List.of(t2.getId()), t2, planned);
        recordVisitResult(orderId, visit, selected, "COMPLETED");
        putReport(previous, validReport()).andExpect(status().isOk());
        finalizeReportRequest(previous).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_AUTHOR_NOT_PARTICIPANT"));
        var reportId = reports.findByServiceOrderId(orderId).orElseThrow().getId();

        assignReportAuthor(previous, t2).andExpect(status().isForbidden());
        assignReportAuthor(login(companyB), t2).andExpect(status().isNotFound());
        assignReportAuthor(company, tb1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.technicianId").isArray());
        assignReportAuthor(company, t1).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.technicianId").isArray());
        moveTechnician(company, t2, a1);
        assignReportAuthor(company, t2).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.technicianId").isArray());
        assertThat(reportAuthorChanges(reportId)).isZero();
        moveTechnician(company, t2, a2);

        assignReportAuthor(company, t2).andExpect(status().isNoContent());
        assignReportAuthor(company, t2).andExpect(status().isNoContent());
        assertThat(reportAuthorChanges(reportId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select previous_author_technician_id from report_author_history "
                + "where service_report_id = ?", UUID.class, reportId)).isEqualTo(t1.getId());
        assertThat(jdbc.queryForObject("select new_author_technician_id from report_author_history "
                + "where service_report_id = ?", UUID.class, reportId)).isEqualTo(t2.getId());
        assertThat(jdbc.queryForObject("select changed_by_user_id from report_author_history "
                + "where service_report_id = ?", UUID.class, reportId)).isEqualTo(companyA.getId());
        assertThat(jdbc.queryForObject("select changed_at from report_author_history where service_report_id = ?",
                OffsetDateTime.class, reportId)).isNotNull();

        mvc.perform(get(reportPath(orderId)).session(company)).andExpect(status().isNotFound());
        mvc.perform(get(reportPath(orderId)).session(previous)).andExpect(status().isNotFound());
        putReport(previous, Map.of()).andExpect(status().isForbidden());
        mvc.perform(get(reportPath(orderId)).session(selected)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId.toString()))
                .andExpect(jsonPath("$.actionsDone").value(validReport().get("actionsDone")));
        finalizeReportRequest(selected).andExpect(status().isOk());
        assignReportAuthor(company, t2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_FINALIZED"));
        assertThat(reportAuthorChanges(reportId)).isEqualTo(1);
    }

    @Test
    void reportAuthorCanBeSelectedBeforeTheFirstDraftUsingAnEarlierCompletedVisit() throws Exception {
        var company = login(companyA);
        var previousCoordinator = login(technicianLogin);
        var nextCoordinator = login(t2.getUser());
        var planned = nextTuesday(9);
        var earlierVisit = confirmVisit(company, List.of(t1.getId()), t1, planned);
        recordVisitResult(orderId, earlierVisit, previousCoordinator, "REVISIT_REQUIRED");
        var laterVisit = postDraft(orderId, company, List.of(t2.getId()), t2.getId());
        confirmDraft(laterVisit, t2, planned.plusHours(2));
        recordVisitResult(orderId, laterVisit, nextCoordinator, "COMPLETED");

        assertThat(reports.findByServiceOrderId(orderId)).isEmpty();
        assignReportAuthor(company, t1).andExpect(status().isNoContent());
        var draft = reports.findByServiceOrderId(orderId).orElseThrow();
        assertThat(draft.getActionsDone()).isNull();
        assertThat(reportAuthorChanges(draft.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select previous_author_technician_id from report_author_history "
                + "where service_report_id = ?", UUID.class, draft.getId())).isNull();
        putReport(previousCoordinator, validReport()).andExpect(status().isOk());
        finalizeReportRequest(previousCoordinator).andExpect(status().isOk())
                .andExpect(jsonPath("$.authorTechnicianId").value(t1.getId().toString()));
    }

    @Test
    void reportConcurrentFirstWritesUseTheSameReportAndOnlyOneFinalizationSucceeds() throws Exception {
        var company = login(companyA);
        var first = login(technicianLogin);
        var second = login(technicianLogin);
        var visit = confirmVisit(company, List.of(t1.getId()), t1, nextTuesday(9));
        recordVisitResult(orderId, visit, first, "COMPLETED");

        var writes = concurrently(() -> putReport(first, validReport()).andReturn(),
                () -> putReport(second, validReport()).andReturn());
        assertThat(writes).allMatch(result -> result.getResponse().getStatus() == 200);
        var reportId = reports.findByServiceOrderId(orderId).orElseThrow().getId().toString();
        for (var write : writes) {
            assertThat(mapper.readTree(write.getResponse().getContentAsString()).get("id").asText()).isEqualTo(reportId);
        }
        assertThat(count("service_report")).isEqualTo(1);

        var finalizations = concurrently(() -> finalizeReportRequest(first).andReturn(),
                () -> finalizeReportRequest(second).andReturn());
        assertThat(finalizations).extracting(result -> result.getResponse().getStatus()).containsExactlyInAnyOrder(200, 409);
        var sent = finalizations.stream().filter(result -> result.getResponse().getStatus() == 200).findFirst().orElseThrow();
        var finalizedAt = mapper.readTree(sent.getResponse().getContentAsString()).get("finalizedAt").asText();
        var stored = reports.findByServiceOrderId(orderId).orElseThrow().getFinalizedAt();
        assertThat(Duration.between(stored, OffsetDateTime.parse(finalizedAt)).abs()).isLessThan(Duration.ofNanos(1_000));
    }

    @Test
    void reportConcurrentDraftWriteCannotChangeAnAlreadyFinalReport() throws Exception {
        var company = login(companyA);
        var writer = login(technicianLogin);
        var finalizer = login(technicianLogin);
        var visit = confirmVisit(company, List.of(t1.getId()), t1, nextTuesday(9));
        recordVisitResult(orderId, visit, writer, "COMPLETED");
        putReport(writer, validReport()).andExpect(status().isOk());
        var replacement = new HashMap<>(validReport());
        replacement.put("actionsDone", "Dodatkowa kontrola filtra");
        var codes = concurrently(() -> putReport(writer, replacement).andReturn().getResponse().getStatus(),
                () -> finalizeReportRequest(finalizer).andReturn().getResponse().getStatus());
        assertThat(codes.getLast()).isEqualTo(200);
        assertThat(codes.getFirst()).isIn(200, 409);
        var report = reports.findByServiceOrderId(orderId).orElseThrow();
        assertThat(report.getFinalizedAt()).isNotNull();
        assertThat(report.getActionsDone()).isEqualTo((codes.getFirst() == 200 ? replacement : validReport()).get("actionsDone"));
    }

    @Test
    void reportConcurrentAuthorChangeAndFinalizationHaveOneWinner() throws Exception {
        var company = login(companyA);
        var original = login(technicianLogin);
        var selected = login(t2.getUser());
        var visit = confirmVisit(company, List.of(t1.getId(), t2.getId()), t1, nextTuesday(9));
        recordVisitResult(orderId, visit, original, "COMPLETED");
        putReport(original, validReport()).andExpect(status().isOk());

        var codes = concurrently(() -> assignReportAuthor(company, t2).andReturn().getResponse().getStatus(),
                () -> finalizeReportRequest(original).andReturn().getResponse().getStatus());
        var report = reports.findByServiceOrderId(orderId).orElseThrow();
        if (codes.getFirst() == 204) {
            assertThat(codes).containsExactly(204, 403);
            assertThat(report.getFinalizedAt()).isNull();
            assertThat(reportAuthorChanges(report.getId())).isEqualTo(1);
            finalizeReportRequest(selected).andExpect(status().isOk())
                    .andExpect(jsonPath("$.authorTechnicianId").value(t2.getId().toString()));
        } else {
            assertThat(codes).containsExactly(409, 200);
            assertThat(report.getFinalizedAt()).isNotNull();
            assertThat(reportAuthorChanges(report.getId())).isZero();
        }
    }

    @Test
    void reportConcurrentFinalizationAndClosingUseTheSameOrderLock() throws Exception {
        var company = login(companyA);
        var author = login(technicianLogin);
        var visit = confirmVisit(company, List.of(t1.getId()), t1, nextTuesday(9));
        recordVisitResult(orderId, visit, author, "COMPLETED");
        putReport(author, validReport()).andExpect(status().isOk());

        var results = concurrently(() -> orderAction(orderId, "close", company).andReturn(),
                () -> finalizeReportRequest(author).andReturn());
        assertThat(results.getLast().getResponse().getStatus()).isEqualTo(200);
        var closeStatus = results.getFirst().getResponse().getStatus();
        if (closeStatus == 409) {
            var problem = mapper.readTree(results.getFirst().getResponse().getContentAsString());
            assertThat(problem.path("code").asText()).isEqualTo("REPORT_NOT_FINALIZED");
            orderAction(orderId, "close", company).andExpect(status().isOk());
        } else {
            assertThat(closeStatus).isEqualTo(200);
        }
        var finalizedAt = reports.findByServiceOrderId(orderId).orElseThrow().getFinalizedAt();
        var closedAt = orders.findById(orderId).orElseThrow().getClosedAt();
        assertThat(closedAt.toInstant()).isAfterOrEqualTo(finalizedAt.toInstant());
        assertThat(statusHistory()).endsWith("IN_PROGRESS", "COMPLETED", "CLOSED");
    }

    private ResultActions putReport(MockHttpSession session, Map<String, Object> data) throws Exception {
        return mvc.perform(putRequest(reportPath(orderId), data).session(session).with(csrf()));
    }

    private static String reportPath(UUID id) {
        return orderPath(id) + "/report";
    }

    private ResultActions finalizeReportRequest(MockHttpSession session) throws Exception {
        return mvc.perform(post(reportPath(orderId) + "/finalize").session(session).with(csrf()));
    }

    private void recordVisitResult(UUID id, UUID visitId, MockHttpSession coordinator, String outcome) throws Exception {
        var path = orderPath(id) + "/visits/" + visitId;
        mvc.perform(post(path + "/start").session(coordinator).with(csrf())).andExpect(status().isOk());
        visitWrite(post(path + "/complete"), coordinator, Map.of("outcome", outcome)).andExpect(status().isOk());
    }

    private ResultActions assignReportAuthor(MockHttpSession session, Technician technician) throws Exception {
        return mvc.perform(putRequest(reportPath(orderId) + "/author", Map.of("technicianId", technician.getId()))
                .session(session).with(csrf()));
    }

    private int reportAuthorChanges(UUID reportId) {
        return jdbc.queryForObject("select count(*) from report_author_history where service_report_id = ?",
                Integer.class, reportId);
    }

    private static Map<String, Object> validReport() {
        return Map.of("actionsDone", "Oczyszczono filtr", "diagnosis", "Zabrudzony filtr",
                "resolution", "Sprawdzono przepływ po czyszczeniu");
    }
}
