package serwio.security;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginStatusController {
    @GetMapping("/api")
    public Map<String, Object> status() {
        return Map.of(
                "message", "Zalogowano pomyślnie",
                "endpoints", List.of(
                        "GET /api/users/me",
                        "GET /api/owners/me",
                        "GET /api/locations",
                        "GET /api/locations/{id}",
                        "POST /api/locations",
                        "PUT /api/locations/{id}",
                        "DELETE /api/locations/{id}",
                        "GET /api/service-objects",
                        "GET /api/service-objects/{id}",
                        "POST /api/service-objects",
                        "PUT /api/service-objects/{id}",
                        "DELETE /api/service-objects/{id}",
                        "GET /api/csrf",
                        "POST /logout"));
    }
}
