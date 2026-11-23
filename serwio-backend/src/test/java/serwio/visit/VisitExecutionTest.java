package serwio.visit;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import serwio.visit.model.ServiceVisit;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VisitExecutionTest extends OrderApiFixture {

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
    void visitConcurrentCompletionAndRepeatDraftsKeepOneOutcomeAndOneActiveVisit() throws Exception {
        var company = login(companyA);
        var firstCoordinator = login(technicianLogin);
        var secondCoordinator = login(technicianLogin);
        var planned = nextTuesday(9);
        var visitId = confirmVisit(company, List.of(t1.getId()), t1, planned);
        var path = orderPath(orderId) + "/visits";
        when(visitClock.instant()).thenReturn(planned.toInstant());
        mvc.perform(post(path + "/" + visitId + "/start").session(firstCoordinator).with(csrf()))
                .andExpect(status().isOk());
        when(visitClock.instant()).thenReturn(planned.plusMinutes(30).toInstant());
        var outcome = Map.of("outcome", "REVISIT_REQUIRED");
        var completion = path + "/" + visitId + "/complete";
        assertThat(concurrently(
                () -> visitWrite(post(completion), firstCoordinator, outcome).andReturn().getResponse().getStatus(),
                () -> visitWrite(post(completion), secondCoordinator, outcome).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(statusHistory()).endsWith("IN_PROGRESS", "REVISIT_REQUIRED");

        var secondCompanySession = login(companyA);
        var staffing = Map.of("technicianIds", List.of(t1.getId()), "coordinatorTechnicianId", t1.getId());
        when(visitClock.instant()).thenReturn(planned.plusMinutes(35).toInstant());
        assertThat(concurrently(
                () -> visitWrite(post(path), company, staffing).andReturn().getResponse().getStatus(),
                () -> visitWrite(post(path), secondCompanySession, staffing).andReturn().getResponse().getStatus()))
                .containsExactlyInAnyOrder(201, 409);
        assertThat(visits.findAllByServiceOrderIdOrderByCreatedAtDescIdAsc(orderId))
                .extracting(ServiceVisit::getStatus).containsExactly("DRAFT", "COMPLETED");
        assertThat(statusHistory()).endsWith("IN_PROGRESS", "REVISIT_REQUIRED", "AWAITING_SCHEDULING");
    }
}
