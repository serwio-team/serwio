package serwio.visit.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.exception.ResourceNotFoundException;
import serwio.order.model.OrderStatus;
import serwio.order.model.OrderStatusHistory;
import serwio.order.repository.OrderStatusHistoryRepository;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.visit.dto.ServiceVisitResponse;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitOutcome;
import serwio.visit.model.VisitStatus;
import serwio.visit.repository.ServiceVisitRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class VisitExecutionService {
    private final ServiceOrderRepository orders;
    private final ServiceVisitRepository visits;
    private final OrderStatusHistoryRepository history;
    private final CurrentUser currentUser;
    private final ServiceVisitService readService;
    private final VisitReservationLocks reservationLocks;
    private final Clock clock;

    public ServiceVisitResponse start(UUID orderId, UUID visitId) {
        var visit = lockCoordinatorVisit(orderId, visitId);
        requireState(visit, VisitStatus.CONFIRMED, OrderStatus.VISIT_CONFIRMED);
        var zone = lockReservationAndCheckAccess(visit);
        var now = OffsetDateTime.now(clock);
        visit.setStartedAt(now);
        changeState(visit, VisitStatus.IN_PROGRESS, OrderStatus.IN_PROGRESS, now);
        return readService.response(visit, zone);
    }

    public ServiceVisitResponse complete(UUID orderId, UUID visitId, VisitOutcome outcome) {
        var visit = lockCoordinatorVisit(orderId, visitId);
        requireState(visit, VisitStatus.IN_PROGRESS, OrderStatus.IN_PROGRESS);
        var zone = lockReservationAndCheckAccess(visit);
        var now = OffsetDateTime.now(clock);
        visit.setCompletedAt(now);
        visit.setOutcome(outcome);
        changeState(visit, VisitStatus.COMPLETED, outcome.orderStatus(), now);
        return readService.response(visit, zone);
    }

    private ServiceVisit lockCoordinatorVisit(UUID orderId, UUID visitId) {
        var userId = currentUser.id();
        orders.findForUpdateByIdForTechnician(orderId, userId)
                .orElseThrow(() -> orderNotFound(orderId));
        var visit = visits.findByIdAndServiceOrderId(visitId, orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono wizyty: " + visitId));
        if (!visit.getCoordinatorTechnician().getUser().getId().equals(userId)) {
            throw new AccessDeniedException("Realizację rejestruje koordynator wizyty.");
        }
        return visit;
    }

    private ZoneId lockReservationAndCheckAccess(ServiceVisit visit) {
        var locked = reservationLocks.lock(visit);
        var orderId = visit.getServiceOrder().getId();
        orders.findByIdForTechnician(orderId, currentUser.id()).orElseThrow(() -> orderNotFound(orderId));
        return ZoneId.of(locked.location().getTimezone());
    }

    private static void requireState(ServiceVisit visit, VisitStatus visitStatus, OrderStatus orderStatus) {
        if (!visitStatus.name().equals(visit.getStatus())
                || !orderStatus.name().equals(visit.getServiceOrder().getCurrentStatus())) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Stan wizyty nie pozwala na tę operację.");
        }
    }

    private void changeState(ServiceVisit visit, VisitStatus visitStatus, OrderStatus orderStatus, OffsetDateTime now) {
        visit.setStatus(visitStatus.name());
        var order = visit.getServiceOrder();
        order.setCurrentStatus(orderStatus.name());
        history.save(OrderStatusHistory.forChange(order, visit.getCoordinatorTechnician().getUser(), now));
    }

    private static ResourceNotFoundException orderNotFound(UUID orderId) {
        return new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId);
    }
}
