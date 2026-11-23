package serwio.visit.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import serwio.visit.dto.VisitTechnicianResponse;
import serwio.visit.service.ServiceVisitService;

@RestController
@RequestMapping("/api/service-orders/{orderId}/visits")
@RequiredArgsConstructor
public class ServiceVisitController {
    private final ServiceVisitService visits;

    @GetMapping("/candidates")
    public List<VisitTechnicianResponse> candidates(@PathVariable UUID orderId) {
        return visits.candidates(orderId);
    }
}
