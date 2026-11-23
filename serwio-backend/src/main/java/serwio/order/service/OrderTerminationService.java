package serwio.order.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ConflictException;
import serwio.exception.ResourceNotFoundException;
import serwio.order.dto.ServiceOrderResponse;
import serwio.order.model.OrderStatus;
import serwio.order.model.OrderStatusHistory;
import serwio.order.model.ServiceOrder;
import serwio.order.repository.OrderBranchAssignmentRepository;
import serwio.order.repository.OrderStatusHistoryRepository;
import serwio.order.repository.OrderTechnicianAssignmentRepository;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.service.VisitStaffingService;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderTerminationService {
    private static final List<String> CANCELLABLE = List.of(OrderStatus.NEW.name(), OrderStatus.ASSIGNED.name(),
            OrderStatus.AWAITING_SCHEDULING.name(), OrderStatus.VISIT_CONFIRMED.name(),
            OrderStatus.AWAITING_TECHNICIAN_REVIEW.name(), OrderStatus.AWAITING_OWNER_CONFIRMATION.name());

    private final ServiceOrderRepository orders;
    private final ServiceCompanyRepository companies;
    private final OrderBranchAssignmentRepository branchAssignments;
    private final OrderTechnicianAssignmentRepository technicianAssignments;
    private final OrderStatusHistoryRepository history;
    private final ServiceVisitRepository visits;
    private final VisitStaffingService staffing;
    private final ServiceOrderService orderReadService;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ServiceOrderResponse cancel(UUID orderId) {
        var order = lockCompanyOrder(orderId);
        if (!CANCELLABLE.contains(order.getCurrentStatus()) || visits.existsByServiceOrderIdAndStartedAtIsNotNull(orderId)) {
            throw new ConflictException("INVALID_ORDER_STATE", "Zlecenie można anulować przed rozpoczęciem realizacji.");
        }
        var now = terminationTime();
        staffing.cancelForOrder(order, now);
        return endOrder(order, OrderStatus.CANCELLED, now);
    }

    private ServiceOrder lockCompanyOrder(UUID orderId) {
        var order = orders.findForUpdateByIdAndServiceCompanyUserId(orderId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
        if (order.getClosedAt() != null) {
            throw new ConflictException("INVALID_ORDER_STATE", "Zlecenie jest już zakończone.");
        }
        return order;
    }

    private OffsetDateTime terminationTime() {
        // The company row also serializes changes in technicians' branch memberships.
        companies.findForUpdateByUserId(currentUser.id()).orElseThrow();
        return OffsetDateTime.now(clock);
    }

    private ServiceOrderResponse endOrder(ServiceOrder order, OrderStatus status, OffsetDateTime now) {
        branchAssignments.findAllByServiceOrderIdAndUnassignedAtIsNull(order.getId())
                .forEach(assignment -> assignment.setUnassignedAt(now));
        technicianAssignments.findAllByServiceOrderIdAndUnassignedAtIsNull(order.getId())
                .forEach(assignment -> assignment.setUnassignedAt(now));
        order.setCurrentStatus(status.name());
        order.setClosedAt(now);
        history.save(OrderStatusHistory.forChange(order, order.getServiceCompany().getUser(), now));
        return orderReadService.response(order);
    }
}
