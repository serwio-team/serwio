package serwio.asset;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceObjectApiTest extends OrderApiFixture {
    @Test
    void listsUpdatesAndSoftDeletesObjectsWithinTheOwnersLocations() throws Exception {
        var session = login(owner);
        var locationId = serviceObject.getLocation().getId();
        var body = objectData(locationId, "  Pompa testowa  ");
        var result = mvc.perform(post("/api/service-objects").session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(body))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Pompa testowa")).andReturn();
        var id = mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
        var path = "/api/service-objects/" + id;
        mvc.perform(get("/api/service-objects").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/service-objects").param("locationId", locationId.toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get("/api/service-objects").param("locationId", UUID.randomUUID().toString()).session(session))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        mvc.perform(putRequest(path, objectData(locationId, "Pompa po zmianie")).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attributes.power").value(12));
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pompa po zmianie"));
        mvc.perform(delete(path).session(session).with(csrf())).andExpect(status().isNoContent());
        assertThat(objects.findById(UUID.fromString(id)).orElseThrow().getActive()).isFalse();
        mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
        mvc.perform(get("/api/service-objects").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(id))));
        mvc.perform(delete(path).session(session).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(putRequest(path, body).session(session).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void cannotMoveAnObjectToAnotherOwnersLocation() throws Exception {
        var foreignOwner = new serwio.owner.model.AssetOwner();
        foreignOwner.setUser(account("ASSET_OWNER", "ACTIVE"));
        foreignOwner.setName("Inny właściciel");
        foreignOwner.setCreatedAt(now());
        owners.save(foreignOwner);
        var foreignObject = objectAtNewLocation(foreignOwner, "Obca lokalizacja");
        var session = login(owner);
        var path = "/api/service-objects/" + serviceObject.getId();
        mvc.perform(putRequest(path, objectData(foreignObject.getLocation().getId(), "Obca pompa"))
                .session(session).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.locationId").value(serviceObject.getLocation().getId().toString()));
        mvc.perform(delete("/api/service-objects/" + foreignObject.getId()).session(session).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private static Map<String, Object> objectData(UUID locationId, String name) {
        return Map.of("locationId", locationId, "name", name, "type", "HEAT_PUMP", "description", "Montaż",
                "model", "M1", "manufacturer", "Test", "serialNumber", "TEST-123", "attributes", Map.of("power", 12));
    }
}
