package serwio.location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import serwio.testsupport.OrderApiFixture;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LocationApiTest extends OrderApiFixture {
    @Test
    void ownerCanListEditAndDeleteLocationWithoutExposingItToAnotherOwner() throws Exception {
        var session = login(owner);
        var other = login(account("ASSET_OWNER", "ACTIVE"));
        var data = locationData("  Pracownia  ");
        var id = createLocation(session, data);
        var path = "/api/locations/" + id;
        mvc.perform(get("/api/locations").param("size", "100").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(id)));
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pracownia"));
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        mvc.perform(putRequest(path, locationData("Obca zmiana")).session(other).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/locations").param("size", "0").session(session)).andExpect(status().isBadRequest());

        mvc.perform(putRequest(path, locationData("Serwis")).session(session).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Serwis"));
        mvc.perform(delete(path).session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get(path).session(session)).andExpect(status().isNotFound());
        mvc.perform(delete(path).session(session).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void invalidLocationDataAndReferencedDeletionAreRejected() throws Exception {
        var session = login(owner);
        var invalid = new HashMap<>(locationData("Pracownia"));
        invalid.put("timezone", "Europe/DoesNotExist");
        invalid.put("country", "pl");
        mvc.perform(post("/api/locations").session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(invalid))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.timezone").isArray()).andExpect(jsonPath("$.errors.country").isArray());
        var withoutProfile = login(account("ASSET_OWNER", "ACTIVE"));
        mvc.perform(post("/api/locations").session(withoutProfile).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(locationData("Pracownia")))).andExpect(status().isForbidden());
        var inUse = "/api/locations/" + serviceObject.getLocation().getId();
        mvc.perform(delete(inUse).session(session).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get(inUse).session(session)).andExpect(status().isOk());
    }

    @Test
    void availabilityNormalizesOvernightWindowsAndRejectsOverlapsWithoutChangingSavedData() throws Exception {
        var session = login(owner);
        var other = login(account("ASSET_OWNER", "ACTIVE"));
        var path = "/api/locations/" + serviceObject.getLocation().getId() + "/availability";
        mvc.perform(get(path).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.days").isEmpty());
        var valid = Map.of("days", List.of(day(7, "22:00", "03:00"), day(3, "10:00", "24:00")));
        writeAvailability(session, path, valid).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].dayOfWeek").value(3))
                .andExpect(jsonPath("$.days[0].intervals[0].to").value("24:00"))
                .andExpect(jsonPath("$.days[1].intervals[0].to").value("03:00"));

        for (var invalid : List.of(
                List.of(day(7, "22:00", "03:00"), day(1, "02:00", "04:00")),
                List.of(day(1, "10:00", "12:00"), day(1, "13:00", "16:00")),
                List.of(day(2, "11:00", "11:00")),
                List.of(day(5, "24:00", "05:00")))) {
            writeAvailability(session, path, Map.of("days", invalid)).andExpect(status().isBadRequest());
        }
        writeAvailability(other, path, valid).andExpect(status().isNotFound());
        mvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        mvc.perform(get(path).session(login(companyA))).andExpect(status().isForbidden());
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[1].dayOfWeek").value(7))
                .andExpect(jsonPath("$.days[1].intervals[0].from").value("22:00"));
        writeAvailability(session, path, Map.of("days", List.of())).andExpect(status().isOk())
                .andExpect(jsonPath("$.days").isEmpty());
    }

    @Test
    void aFullDayCannotBeOverlappedByAnotherWindowOnTheSameDay() throws Exception {
        var session = login(owner);
        var path = "/api/locations/" + serviceObject.getLocation().getId() + "/availability";
        var fullDay = Map.<String, Object>of("days", List.of(day(1, "00:00", "24:00")));
        writeAvailability(session, path, fullDay).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].intervals[0].to").value("24:00"));
        var overlap = Map.<String, Object>of("days", List.of(Map.of("dayOfWeek", 1, "intervals", List.of(
                Map.of("from", "00:00", "to", "24:00"), Map.of("from", "12:00", "to", "15:00")))));
        writeAvailability(session, path, overlap).andExpect(status().isBadRequest());
        mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].intervals.length()").value(1));
    }

    private ResultActions writeAvailability(MockHttpSession session, String path, Map<String, ?> data) throws Exception {
        return mvc.perform(putRequest(path, data).session(session).with(csrf()));
    }

    private String createLocation(MockHttpSession session, Map<String, Object> data) throws Exception {
        var result = mvc.perform(post("/api/locations").session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(data))).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).path("id").asText();
    }

    private static Map<String, Object> locationData(String name) {
        return Map.of("name", name, "addressLine", "Ulica 10", "city", "Warszawa", "postalCode", "00-001",
                "country", "PL", "timezone", "Europe/Warsaw");
    }

    private static Map<String, Object> day(int day, String from, String to) {
        return Map.of("dayOfWeek", day, "intervals", List.of(Map.of("from", from, "to", to)));
    }
}
