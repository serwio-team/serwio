package serwio.order;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import serwio.testsupport.OrderApiFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderCreationApiTest extends OrderApiFixture {
    @Test
    void rejectsForeignInactiveAndUnconfiguredOrderCreationWithoutWritingAnOrder() throws Exception {
        var session = login(owner);
        var foreign = login(account("ASSET_OWNER", "ACTIVE"));
        var before = orders.count();
        createOrder(foreign, serviceObject.getId(), "REPAIR").andExpect(status().isNotFound());
        createOrder(session, UUID.randomUUID(), "REPAIR").andExpect(status().isNotFound());
        createOrder(session, serviceObject.getId(), "OTHER").andExpect(status().isBadRequest());
        createOrder(session, serviceObject.getId(), "INSPECTION").andExpect(status().isServiceUnavailable());
        mvc.perform(delete("/api/service-objects/" + serviceObject.getId()).session(session).with(csrf()))
                .andExpect(status().isNoContent());
        createOrder(session, serviceObject.getId(), "REPAIR").andExpect(status().isNotFound());
        assertThat(orders.count()).isEqualTo(before);
    }

    private ResultActions createOrder(MockHttpSession session, UUID objectId, String type) throws Exception {
        var payload = Map.of("serviceObjectId", objectId, "orderType", type, "description", "Potrzebny serwis");
        return mvc.perform(post(ORDERS_PATH).session(session).with(csrf()).contentType("application/json")
                .content(mapper.writeValueAsString(payload)));
    }
}
