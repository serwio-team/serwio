package serwio.report.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ConflictException;
import serwio.exception.FieldValidationException;
import serwio.exception.ResourceNotFoundException;
import serwio.order.model.OrderStatus;
import serwio.order.model.ServiceOrder;
import serwio.order.repository.ServiceOrderRepository;
import serwio.report.dto.ReportDraftRequest;
import serwio.report.dto.ServiceReportResponse;
import serwio.report.model.ReportAuthorHistory;
import serwio.report.model.ServiceReport;
import serwio.report.repository.ReportAuthorHistoryRepository;
import serwio.report.repository.ServiceReportRepository;
import serwio.security.CurrentUser;
import serwio.technician.model.Technician;
import serwio.technician.repository.TechnicianRepository;
import serwio.visit.model.VisitStatus;
import serwio.visit.repository.VisitTechnicianRepository;

@Service
@RequiredArgsConstructor
public class ServiceReportService {
    private final ServiceOrderRepository orders;
    private final ServiceReportRepository reports;
    private final ReportAuthorHistoryRepository authorHistory;
    private final UserAccountRepository accounts;
    private final TechnicianRepository technicians;
    private final VisitTechnicianRepository visitTechnicians;
    private final CurrentUser currentUser;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ServiceReportResponse get(UUID orderId) {
        var userId = currentUser.id();
        var actor = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika."));
        var order = switch (actor.getRole()) {
            case "ASSET_OWNER" -> orders.findByIdAndServiceObjectLocationAssetOwnerUserId(orderId, userId);
            case "SERVICE_COMPANY" -> orders.findByIdAndServiceCompanyUserId(orderId, userId);
            case "TECHNICIAN" -> orders.findByIdForTechnician(orderId, userId);
            default -> throw new AccessDeniedException("Konto nie ma dostępu do raportów.");
        };
        var authorizedOrder = order.orElseThrow(ServiceReportService::reportNotFound);
        var report = reports.findByServiceOrderId(orderId).orElseThrow(ServiceReportService::reportNotFound);
        if (report.getFinalizedAt() == null && (authorizedOrder.getClosedAt() != null
                || !"TECHNICIAN".equals(actor.getRole()) || !isAuthor(report, userId))) {
            throw reportNotFound();
        }
        return ServiceReportResponse.from(report);
    }

    @Transactional
    public ServiceReportResponse saveDraft(UUID orderId, ReportDraftRequest request) {
        var userId = currentUser.id();
        var order = lockOrderForAuthor(orderId, userId);
        var report = reports.findByServiceOrderId(orderId).orElseGet(() -> {
            var coordinator = order.getCoordinatorTechnician();
            if (coordinator == null) {
                throw new ConflictException("REPORT_AUTHOR_NOT_ASSIGNED", "Zlecenie nie ma wskazanego autora raportu.");
            }
            return newDraft(order, coordinator);
        });
        requireWritableAuthor(report, userId);
        report.setActionsDone(text(request.actionsDone()));
        report.setDiagnosis(text(request.diagnosis()));
        report.setResolution(text(request.resolution()));
        report.setUsedMaterials(text(request.usedMaterials()));
        report.setRecommendations(text(request.recommendations()));
        report.setUpdatedAt(OffsetDateTime.now(clock));
        return ServiceReportResponse.from(reports.save(report));
    }

    @Transactional
    public ServiceReportResponse finalizeReport(UUID orderId) {
        var userId = currentUser.id();
        var order = lockOrderForAuthor(orderId, userId);
        var report = reports.findByServiceOrderId(orderId).orElseThrow(ServiceReportService::reportNotFound);
        requireWritableAuthor(report, userId);
        if (!OrderStatus.COMPLETED.name().equals(order.getCurrentStatus())) {
            throw new ConflictException("INVALID_ORDER_STATE", "Raport można sfinalizować po wykonaniu zlecenia.");
        }
        if (!participatedInCompletedVisit(orderId, report.getAuthorTechnician().getId())) {
            throw new ConflictException("REPORT_AUTHOR_NOT_PARTICIPANT",
                    "Autor raportu musi być w obsadzie zakończonej wizyty zlecenia.");
        }
        validateFinalContent(report);
        report.setFinalizedAt(OffsetDateTime.now(clock));
        return ServiceReportResponse.from(report);
    }

    @Transactional
    public void assignAuthor(UUID orderId, UUID technicianId) {
        var userId = currentUser.id();
        var order = orders.findForUpdateByIdAndServiceCompanyUserId(orderId, userId)
                .orElseThrow(ServiceReportService::reportNotFound);
        if (order.getClosedAt() != null) {
            throw new ConflictException("INVALID_ORDER_STATE", "Zlecenie jest już zakończone.");
        }
        var existing = reports.findByServiceOrderId(orderId);
        existing.ifPresent(ServiceReportService::requireDraft);
        var candidate = technicians.findForUpdateByIdAndBranchServiceCompanyUserId(technicianId, userId)
                .filter(technician -> orders.hasCurrentTechnicianAccess(orderId, technician.getUser().getId()))
                .orElseThrow(() -> invalidAuthor("Wybierz serwisanta z dostępem do zlecenia."));
        if (!participatedInCompletedVisit(orderId, technicianId)) {
            throw invalidAuthor("Autor musi być w obsadzie zakończonej wizyty zlecenia.");
        }
        var previous = existing.map(ServiceReport::getAuthorTechnician).orElse(null);
        if (previous != null && previous.getId().equals(technicianId)) return;

        var report = existing.orElseGet(() -> newDraft(order, candidate));
        report.setAuthorTechnician(candidate);
        authorHistory.save(new ReportAuthorHistory(reports.save(report), previous, candidate,
                order.getServiceCompany().getUser(), OffsetDateTime.now(clock)));
    }

    private static FieldValidationException invalidAuthor(String message) {
        return new FieldValidationException(Map.of("technicianId", List.of(message)));
    }

    private boolean participatedInCompletedVisit(UUID orderId, UUID technicianId) {
        return visitTechnicians.existsByVisitServiceOrderIdAndTechnicianIdAndVisitStatus(
                orderId, technicianId, VisitStatus.COMPLETED.name());
    }

    private static void validateFinalContent(ServiceReport report) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        requireText(errors, "actionsDone", report.getActionsDone());
        requireText(errors, "diagnosis", report.getDiagnosis());
        requireText(errors, "resolution", report.getResolution());
        if (!errors.isEmpty()) throw new FieldValidationException(errors);
    }

    private static void requireText(Map<String, List<String>> errors, String field, String value) {
        if (value == null || value.isBlank()) errors.put(field, List.of("Pole jest wymagane do finalizacji."));
    }

    private ServiceOrder lockOrderForAuthor(UUID orderId, UUID userId) {
        return orders.findForUpdateByIdForTechnician(orderId, userId)
                .orElseThrow(ServiceReportService::reportNotFound);
    }

    private ServiceReport newDraft(ServiceOrder order, Technician author) {
        var report = new ServiceReport();
        report.setServiceOrder(order);
        report.setAuthorTechnician(author);
        report.setCreatedAt(OffsetDateTime.now(clock));
        return report;
    }

    private void requireWritableAuthor(ServiceReport report, UUID userId) {
        if (!isAuthor(report, userId)) {
            throw new AccessDeniedException("Raport może zmieniać tylko jego autor.");
        }
        technicians.findForUpdateById(report.getAuthorTechnician().getId())
                .orElseThrow(ServiceReportService::reportNotFound);
        if (!orders.hasCurrentTechnicianAccess(report.getServiceOrder().getId(), userId)) {
            throw reportNotFound();
        }
        requireDraft(report);
    }

    private static boolean isAuthor(ServiceReport report, UUID userId) {
        return report.getAuthorTechnician().getUser().getId().equals(userId);
    }

    private static void requireDraft(ServiceReport report) {
        if (report.getFinalizedAt() != null) {
            throw new ConflictException("REPORT_ALREADY_FINALIZED", "Raport jest już sfinalizowany.");
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ResourceNotFoundException reportNotFound() {
        return new ResourceNotFoundException("Nie znaleziono raportu dla tego zlecenia.");
    }
}
