package serwio.security;

import org.junit.jupiter.api.Test;
import serwio.testsupport.OrderApiFixture;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginContentNegotiationTest extends OrderApiFixture {
    @Test
    void browserRequestsRedirectAndJsonRequestsReceiveHttpErrors() throws Exception {
        mvc.perform(post("/login").with(csrf()).header("Accept", "text/html")
                .param("username", owner.getEmail()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/api"));
        mvc.perform(post("/login").with(csrf()).header("Accept", "text/html")
                .param("username", owner.getEmail()).param("password", "wrong"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).header("Accept", "text/html, application/json;q=0")
                .param("username", owner.getEmail()).param("password", "wrong"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/login").with(csrf()).header("Accept", "invalid-media-type")
                .param("username", owner.getEmail()).param("password", "wrong"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(post("/login").with(csrf()).header("Accept", "application/json")
                .param("username", owner.getEmail()).param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").isString());
    }
}
