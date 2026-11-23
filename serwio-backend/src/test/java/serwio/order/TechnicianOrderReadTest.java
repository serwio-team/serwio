package serwio.order;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import serwio.testsupport.OrderApiFixture;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TechnicianOrderReadTest extends OrderApiFixture {

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
}
