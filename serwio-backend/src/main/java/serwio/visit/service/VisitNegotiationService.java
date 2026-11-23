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
import serwio.account.repository.UserAccountRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.location.model.Location;
import serwio.location.repository.LocationAvailabilityRepository;
import serwio.order.model.OrderStatus;
import serwio.order.model.OrderStatusHistory;
import serwio.order.repository.OrderStatusHistoryRepository;
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
    private final OrderStatusHistoryRepository history;
    private final VisitReservationLocks reservationLocks;
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;
    private final ServiceVisitService readService;

    public ServiceVisitResponse propose(UUID orderId, UUID visitId, VisitTimeRange request) {
        var userId = currentUser.id();
        var order = orders.findForUpdateByIdForTechnician(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
        var visit = findVisit(visitId, orderId);
        var coordinator = requireCoordinator(visit, userId);
        requireDraft(visit);
        if (proposals.findFirstByVisitIdAndStatusOrderByCreatedAtDescIdAsc(visitId, VisitProposalStatus.PENDING.name())
                .isPresent()) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Wizyta ma już propozycję oczekującą na odpowiedź.");
        }
        var location = order.getServiceObject().getLocation();
        var zone = ZoneId.of(location.getTimezone());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        VisitCalendar.validateRequest(request, zone, now.toInstant());
        requireAvailable(visit, request, location, now, technicians.findTechnicianIds(visitId));
        saveProposal(visit, request, coordinator, now);
        return readService.response(visit, zone);
    }

    public ServiceVisitResponse reject(UUID orderId, UUID visitId, UUID proposalId) {
        var reply = prepareReply(orderId, visitId, proposalId);
        respond(reply, VisitProposalStatus.REJECTED, OffsetDateTime.now(ZoneOffset.UTC));
        return readService.response(reply.visit(), ZoneId.of(reply.location().getTimezone()));
    }

    public ServiceVisitResponse counter(UUID orderId, UUID visitId, UUID proposalId, VisitTimeRange request) {
        var reply = prepareReply(orderId, visitId, proposalId);
        var location = reply.location();
        var zone = ZoneId.of(location.getTimezone());
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        VisitCalendar.validateRequest(request, zone, now.toInstant());
        requireAvailable(reply.visit(), request, location, now, technicians.findTechnicianIds(visitId));
        respond(reply, VisitProposalStatus.COUNTERED, now);
        saveProposal(reply.visit(), request, reply.actor(), now);
        return readService.response(reply.visit(), zone);
    }

    public ServiceVisitResponse accept(UUID orderId, UUID visitId, UUID proposalId) {
        var reply = prepareReply(orderId, visitId, proposalId);
        var visit = reply.visit();
        var locked = reservationLocks.lock(visit);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var proposal = reply.proposal();
        var range = new VisitTimeRange(proposal.getProposedStartsAt(), proposal.getProposedEndsAt());
        requireAvailable(visit, range, locked.location(), now, locked.technicianIds());

        respond(reply, VisitProposalStatus.ACCEPTED, now);
        visit.setAcceptedProposal(proposal);
        visit.setStartsAt(range.startsAt());
        visit.setEndsAt(range.endsAt());
        visit.setStatus(VisitStatus.CONFIRMED.name());
        var order = visit.getServiceOrder();
        order.setCurrentStatus(OrderStatus.VISIT_CONFIRMED.name());
        history.save(OrderStatusHistory.forChange(order, reply.actor(), now));
        return readService.response(visit, ZoneId.of(locked.location().getTimezone()));
    }

    private Reply prepareReply(UUID orderId, UUID visitId, UUID proposalId) {
        var userId = currentUser.id();
        var actor = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika."));
        var lockedOrder = switch (actor.getRole()) {
            case "ASSET_OWNER" -> orders.findForUpdateByIdForOwner(orderId, userId);
            case "TECHNICIAN" -> orders.findForUpdateByIdForTechnician(orderId, userId);
            default -> throw new AccessDeniedException("Konto nie uczestniczy w uzgadnianiu terminu.");
        };
        lockedOrder.orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
        var visit = findVisit(visitId, orderId);
        if ("TECHNICIAN".equals(actor.getRole())) requireCoordinator(visit, userId);
        var proposal = proposals.findByIdAndVisitIdAndStatus(proposalId, visitId, VisitProposalStatus.PENDING.name())
                .orElseThrow(() -> new VisitConflictException("STALE_PROPOSAL", "Propozycja nie jest już aktualna."));
        if (actor.getRole().equals(proposal.getProposedByUser().getRole())) {
            throw new AccessDeniedException("Na propozycję odpowiada druga strona uzgodnienia.");
        }
        requireDraft(visit);
        return new Reply(visit, proposal, actor);
    }

    private static void respond(Reply reply, VisitProposalStatus status, OffsetDateTime now) {
        reply.proposal().setStatus(status.name());
        reply.proposal().setRespondedByUser(reply.actor());
        reply.proposal().setRespondedAt(now);
    }

    private ServiceVisit findVisit(UUID visitId, UUID orderId) {
        return visits.findByIdAndServiceOrderId(visitId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono wizyty: " + visitId));
    }

    private static UserAccount requireCoordinator(ServiceVisit visit, UUID userId) {
        var coordinator = visit.getCoordinatorTechnician().getUser();
        if (!coordinator.getId().equals(userId)) {
            throw new AccessDeniedException("W uzgodnieniu uczestniczy koordynator wizyty.");
        }
        return coordinator;
    }

    private static void requireDraft(ServiceVisit visit) {
        if (!VisitStatus.DRAFT.name().equals(visit.getStatus()) || visit.getServiceOrder().getClosedAt() != null
                || !OrderStatus.AWAITING_SCHEDULING.name().equals(visit.getServiceOrder().getCurrentStatus())) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Wizyta nie jest w trakcie uzgadniania terminu.");
        }
    }

    private void requireAvailable(ServiceVisit visit, VisitTimeRange range, Location location,
                                  OffsetDateTime now, List<UUID> staff) {
        var eligible = orders.findEligibleTechnicians(visit.getServiceOrder().getId()).stream()
                .map(Technician::getId).collect(Collectors.toSet());
        if (!staff.contains(visit.getCoordinatorTechnician().getId()) || !eligible.containsAll(staff)) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Obsada wizyty nie ma już dostępu do zlecenia.");
        }
        VisitCalendar.requireAvailable(range, ZoneId.of(location.getTimezone()),
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

    private record Reply(ServiceVisit visit, VisitProposal proposal, UserAccount actor) {
        Location location() {
            return visit.getServiceOrder().getServiceObject().getLocation();
        }
    }
}
