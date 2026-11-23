package serwio.visit.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.model.UserAccount;
import serwio.exception.ResourceNotFoundException;
import serwio.location.model.Location;
import serwio.location.repository.LocationAvailabilityRepository;
import serwio.order.model.OrderStatus;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.technician.model.Technician;
import serwio.visit.dto.ServiceVisitResponse;
import serwio.visit.dto.VisitTimeRange;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitProposal;
import serwio.visit.model.VisitProposalStatus;
import serwio.visit.model.VisitStatus;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.repository.VisitProposalRepository;
import serwio.visit.repository.VisitTechnicianRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitNegotiationService {
    private final ServiceOrderRepository orders;
    private final ServiceVisitRepository visits;
    private final VisitProposalRepository proposals;
    private final VisitTechnicianRepository technicians;
    private final LocationAvailabilityRepository availability;
    private final CurrentUser currentUser;
    private final ServiceVisitService readService;

    public ServiceVisitResponse propose(UUID orderId, UUID visitId, VisitTimeRange request) {
        var userId = currentUser.id();
        var order = orders.findForUpdateByIdForTechnician(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
        var visit = draft(visitId, orderId);
        var coordinator = visit.getCoordinatorTechnician().getUser();
        if (!coordinator.getId().equals(userId)) {
            throw new AccessDeniedException("Termin proponuje koordynator wizyty.");
        }
        if (proposals.findFirstByVisitIdAndStatusOrderByCreatedAtDescIdAsc(visitId, VisitProposalStatus.PENDING.name())
                .isPresent()) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Wizyta ma już propozycję oczekującą na odpowiedź.");
        }
        var location = order.getServiceObject().getLocation();
        var zone = ZoneId.of(location.getTimezone());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        VisitCalendar.validateRequest(request, zone, now.toInstant());
        requireAvailable(visit, request, location, zone, now);
        saveProposal(visit, request, coordinator, now);
        return readService.response(visit, zone);
    }

    private ServiceVisit draft(UUID visitId, UUID orderId) {
        var visit = visits.findByIdAndServiceOrderId(visitId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono wizyty: " + visitId));
        if (!VisitStatus.DRAFT.name().equals(visit.getStatus()) || visit.getServiceOrder().getClosedAt() != null
                || !OrderStatus.AWAITING_SCHEDULING.name().equals(visit.getServiceOrder().getCurrentStatus())) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Wizyta nie jest w trakcie uzgadniania terminu.");
        }
        return visit;
    }

    private void requireAvailable(ServiceVisit visit, VisitTimeRange range, Location location, ZoneId zone,
                                  OffsetDateTime now) {
        var staff = technicians.findAllByVisitIdOrderByTechnicianDisplayNameAscTechnicianIdAsc(visit.getId()).stream()
                .map(member -> member.getId().getTechnicianId()).toList();
        var eligible = orders.findEligibleTechnicians(visit.getServiceOrder().getId()).stream()
                .map(Technician::getId).collect(Collectors.toSet());
        if (!staff.contains(visit.getCoordinatorTechnician().getId()) || !eligible.containsAll(staff)) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Obsada wizyty nie ma już dostępu do zlecenia.");
        }
        VisitCalendar.requireAvailable(range, zone,
                availability.findAllByLocationIdOrderByDayOfWeekAscAvailableFromAsc(location.getId()), now.toInstant());
        if (technicians.hasOverlap(staff, List.of(VisitStatus.CONFIRMED.name(), VisitStatus.IN_PROGRESS.name()),
                range.startsAt(), range.endsAt())) {
            throw new VisitConflictException("TECHNICIAN_TIME_CONFLICT", "Co najmniej jeden wykonawca ma kolidującą wizytę.");
        }
    }

    private void saveProposal(ServiceVisit visit, VisitTimeRange range, UserAccount author, OffsetDateTime now) {
        var proposal = new VisitProposal();
        proposal.setVisit(visit);
        proposal.setProposedByUser(author);
        proposal.setProposedStartsAt(range.startsAt());
        proposal.setProposedEndsAt(range.endsAt());
        proposal.setStatus(VisitProposalStatus.PENDING.name());
        proposal.setCreatedAt(now);
        proposals.save(proposal);
    }
}
