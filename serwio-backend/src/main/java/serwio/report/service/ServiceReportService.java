package serwio.report.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ConflictException;
import serwio.exception.ResourceNotFoundException;
import serwio.order.model.ServiceOrder;
import serwio.order.repository.ServiceOrderRepository;
import serwio.report.dto.ReportDraftRequest;
import serwio.report.dto.ServiceReportResponse;
import serwio.report.model.ServiceReport;
import serwio.report.repository.ServiceReportRepository;
import serwio.security.CurrentUser;
import serwio.technician.model.Technician;
import serwio.technician.repository.TechnicianRepository;

@Service
@RequiredArgsConstructor
public class ServiceReportService {
    private final ServiceOrderRepository orders;
    private final ServiceReportRepository reports;
    private final UserAccountRepository accounts;
    private final TechnicianRepository technicians;
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
