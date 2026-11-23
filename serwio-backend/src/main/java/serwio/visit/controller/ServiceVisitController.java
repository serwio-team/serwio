package serwio.visit.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import serwio.visit.dto.ServiceVisitResponse;
import serwio.visit.dto.ServiceVisitsResponse;
import serwio.visit.dto.VisitStaffingRequest;
import serwio.visit.dto.VisitTechnicianResponse;
import serwio.visit.dto.VisitTimeRange;
import serwio.visit.service.ServiceVisitService;
import serwio.visit.service.VisitNegotiationService;
import serwio.visit.service.VisitStaffingService;

@RestController
@RequestMapping("/api/service-orders/{orderId}/visits")
@RequiredArgsConstructor
public class ServiceVisitController {
    private final ServiceVisitService visits;
    private final VisitStaffingService staffing;
    private final VisitNegotiationService negotiation;

    @GetMapping
    public ServiceVisitsResponse list(@PathVariable UUID orderId) {
        return visits.list(orderId);
    }

    @GetMapping("/candidates")
    public List<VisitTechnicianResponse> candidates(@PathVariable UUID orderId) {
        return visits.candidates(orderId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceVisitResponse create(@PathVariable UUID orderId, @Valid @RequestBody VisitStaffingRequest request) {
        return staffing.create(orderId, request);
    }

    @PutMapping("/{visitId}/staffing")
    public ServiceVisitResponse replaceStaffing(@PathVariable UUID orderId, @PathVariable UUID visitId,
                                                @Valid @RequestBody VisitStaffingRequest request) {
        return staffing.replace(orderId, visitId, request);
    }

    @PostMapping("/{visitId}/proposals")
    public ServiceVisitResponse propose(@PathVariable UUID orderId, @PathVariable UUID visitId,
                                        @Valid @RequestBody VisitTimeRange request) {
        return negotiation.propose(orderId, visitId, request);
    }

    @PostMapping("/{visitId}/proposals/{proposalId}/reject")
    public ServiceVisitResponse reject(@PathVariable UUID orderId, @PathVariable UUID visitId,
                                       @PathVariable UUID proposalId) {
        return negotiation.reject(orderId, visitId, proposalId);
    }

    @PostMapping("/{visitId}/proposals/{proposalId}/counter")
    public ServiceVisitResponse counter(@PathVariable UUID orderId, @PathVariable UUID visitId,
                                        @PathVariable UUID proposalId, @Valid @RequestBody VisitTimeRange request) {
        return negotiation.counter(orderId, visitId, proposalId, request);
    }

    @PostMapping("/{visitId}/proposals/{proposalId}/accept")
    public ServiceVisitResponse accept(@PathVariable UUID orderId, @PathVariable UUID visitId,
                                       @PathVariable UUID proposalId) {
        return negotiation.accept(orderId, visitId, proposalId);
    }

    @PostMapping("/{visitId}/cancel")
    public ServiceVisitResponse cancel(@PathVariable UUID orderId, @PathVariable UUID visitId) {
        return staffing.cancel(orderId, visitId);
    }
}
