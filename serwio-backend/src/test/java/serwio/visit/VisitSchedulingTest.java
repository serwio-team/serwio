package serwio.visit;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitProposal;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VisitSchedulingTest extends OrderApiFixture {
    @Test
    void replacesVisitCoordinatorAndKeepsOnlySelectedStaffBeforeConfirmation() throws Exception {
        var company = login(companyA);
        put(company, body(0, List.of(a1.getId(), a2.getId()), List.of(), t1.getId())).andExpect(status().isOk());
        var visitId = postDraft(orderId, company, List.of(t1.getId(), t2.getId()), t1.getId());
        var visitPath = orderPath(orderId) + "/visits/" + visitId;
        var sameStaffNewCoordinator = Map.<String, Object>of(
                "technicianIds", List.of(t1.getId(), t2.getId()), "coordinatorTechnicianId", t2.getId());
        mvc.perform(putRequest(visitPath + "/staffing", sameStaffNewCoordinator).session(company).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coordinatorTechnicianId").value(t2.getId().toString()));
        var single = Map.<String, Object>of("technicianIds", List.of(t2.getId()), "coordinatorTechnicianId", t2.getId());
        mvc.perform(putRequest(visitPath + "/staffing", single).session(company).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.technicians[*].id", contains(t2.getId().toString())));
        mvc.perform(putRequest(visitPath + "/staffing", sameStaffNewCoordinator).session(company).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.technicians[*].id", containsInAnyOrder(
                        t1.getId().toString(), t2.getId().toString())));
        confirmDraft(visitId, t2, nextTuesday(9));
        mvc.perform(putRequest(visitPath + "/staffing", single).session(company).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_VISIT_STATE"));
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
}
