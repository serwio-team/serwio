package serwio.report.controller;

import jakarta.validation.Valid;
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
import serwio.report.dto.ReportDraftRequest;
import serwio.report.dto.ReportAuthorRequest;
import serwio.report.dto.ServiceReportResponse;
import serwio.report.service.ServiceReportService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/service-orders/{orderId}/report")
public class ServiceReportController {
    private final ServiceReportService reports;

    @GetMapping
    public ServiceReportResponse get(@PathVariable UUID orderId) {
        return reports.get(orderId);
    }

    @PutMapping
    public ServiceReportResponse saveDraft(@PathVariable UUID orderId, @Valid @RequestBody ReportDraftRequest request) {
        return reports.saveDraft(orderId, request);
    }

    @PostMapping("/finalize")
    public ServiceReportResponse finalizeReport(@PathVariable UUID orderId) {
        return reports.finalizeReport(orderId);
    }

    @PutMapping("/author")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignAuthor(@PathVariable UUID orderId, @Valid @RequestBody ReportAuthorRequest request) {
        reports.assignAuthor(orderId, request.technicianId());
    }
}
