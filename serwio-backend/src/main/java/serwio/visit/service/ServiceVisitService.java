package serwio.visit.service;

import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.location.service.LocationAvailabilityService;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.visit.dto.ServiceVisitResponse;
import serwio.visit.dto.ServiceVisitsResponse;
import serwio.visit.dto.VisitTechnicianResponse;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitProposalStatus;
import serwio.visit.model.VisitTechnician;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.repository.VisitProposalRepository;
import serwio.visit.repository.VisitTechnicianRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceVisitService {
    private final ServiceOrderRepository orders;
    private final ServiceVisitRepository visits;
    private final VisitTechnicianRepository technicians;
    private final VisitProposalRepository proposals;
    private final LocationAvailabilityService availability;
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;

    public List<VisitTechnicianResponse> candidates(UUID orderId) {
        orders.findByIdAndServiceCompanyUserId(orderId, currentUser.id())
                .orElseThrow(() -> orderNotFound(orderId));
        return orders.findEligibleTechnicians(orderId).stream().map(VisitTechnicianResponse::from).toList();
    }

    public ServiceVisitsResponse list(UUID orderId) {
        var userId = currentUser.id();
        var account = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika."));
        var order = switch (account.getRole()) {
            case "ASSET_OWNER" -> orders.findByIdAndServiceObjectLocationAssetOwnerUserId(orderId, userId);
            case "SERVICE_COMPANY" -> orders.findByIdAndServiceCompanyUserId(orderId, userId);
            case "TECHNICIAN" -> orders.findByIdForTechnician(orderId, userId);
            default -> throw new AccessDeniedException("Brak dostępu do wizyt.");
        };
        var location = order.orElseThrow(() -> orderNotFound(orderId)).getServiceObject().getLocation();
        var schedule = availability.getForAuthorizedLocation(location);
        var zone = ZoneId.of(schedule.timezone());
        return new ServiceVisitsResponse(schedule.timezone(), schedule.days(), visits
                .findAllByServiceOrderIdOrderByCreatedAtDescIdAsc(orderId).stream()
                .map(visit -> response(visit, zone)).toList());
    }

    ServiceVisitResponse response(ServiceVisit visit, ZoneId zone) {
        var staff = technicians.findAllByVisitIdOrderByTechnicianDisplayNameAscTechnicianIdAsc(visit.getId())
                .stream().map(VisitTechnician::getTechnician).map(VisitTechnicianResponse::from).toList();
        var proposal = proposals.findFirstByVisitIdAndStatusOrderByCreatedAtDescIdAsc(
                        visit.getId(), VisitProposalStatus.PENDING.name())
                .map(p -> new ServiceVisitResponse.Proposal(p.getId(),
                        "ASSET_OWNER".equals(p.getProposedByUser().getRole()) ? "OWNER" : "COORDINATOR",
                        inZone(p.getProposedStartsAt(), zone), inZone(p.getProposedEndsAt(), zone)))
                .orElse(null);
        return new ServiceVisitResponse(visit.getId(), visit.getStatus(), visit.getCoordinatorTechnician().getId(),
                staff, proposal, inZone(visit.getStartsAt(), zone), inZone(visit.getEndsAt(), zone));
    }

    private static OffsetDateTime inZone(OffsetDateTime time, ZoneId zone) {
        return time == null ? null : time.atZoneSameInstant(zone).toOffsetDateTime();
    }

    private static ResourceNotFoundException orderNotFound(UUID orderId) {
        return new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId);
    }
}
