package serwio.order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderAssignmentTest extends OrderApiFixture {

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

    private void assertNothingWritten() {
        assertThat(count("order_branch_assignment")).isZero();
        assertThat(count("order_technician_assignment")).isZero();
        assertThat(statusHistory()).isEmpty();
        assertThat(currentStatus()).isNotEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("select assignment_version from service_order where id = ?", Long.class, orderId))
                .isZero();
    }
}
