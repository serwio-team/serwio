package serwio.visit.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.exception.FieldValidationException;
import serwio.exception.ResourceNotFoundException;
import serwio.order.model.OrderStatus;
import serwio.order.model.OrderStatusHistory;
import serwio.order.model.ServiceOrder;
import serwio.order.repository.OrderStatusHistoryRepository;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.technician.model.Technician;
import serwio.visit.dto.ServiceVisitResponse;
import serwio.visit.dto.VisitStaffingRequest;
import serwio.visit.model.ServiceVisit;
import serwio.visit.model.VisitStatus;
import serwio.visit.model.VisitTechnician;
import serwio.visit.model.VisitTechnicianId;
import serwio.visit.repository.ServiceVisitRepository;
import serwio.visit.repository.VisitTechnicianRepository;

@Service
@RequiredArgsConstructor
public class VisitStaffingService {
    private final ServiceOrderRepository orders;
    private final ServiceVisitRepository visits;
    private final VisitTechnicianRepository technicians;
    private final OrderStatusHistoryRepository history;
    private final CurrentUser currentUser;
    private final ServiceVisitService readService;

    @Transactional
    public ServiceVisitResponse create(UUID orderId, VisitStaffingRequest request) {
        var order = lockCompanyOrder(orderId);
        if (order.getClosedAt() != null || !OrderStatus.ASSIGNED.name().equals(order.getCurrentStatus())
                || visits.existsByServiceOrderIdAndStatusNotIn(orderId, List.of(
                        VisitStatus.CANCELLED.name(), VisitStatus.COMPLETED.name()))) {
            throw new VisitConflictException("INVALID_VISIT_STATE", "Nie można utworzyć wizyty dla tego zlecenia.");
        }
        var staff = validateStaff(orderId, request);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var visit = new ServiceVisit();
        visit.setServiceOrder(order);
        visit.setCoordinatorTechnician(coordinator(staff, request.coordinatorTechnicianId()));
        visit.setStatus(VisitStatus.DRAFT.name());
        visit.setCreatedAt(now);
        visits.save(visit);
        technicians.saveAll(staff.stream().map(technician -> membership(visit, technician)).toList());

        order.setCurrentStatus(OrderStatus.AWAITING_SCHEDULING.name());
        var entry = new OrderStatusHistory();
        entry.setServiceOrder(order);
        entry.setStatus(order.getCurrentStatus());
        entry.setChangedByUser(order.getServiceCompany().getUser());
        entry.setChangedAt(now);
        history.save(entry);
        return readService.response(visit, ZoneId.of(order.getServiceObject().getLocation().getTimezone()));
    }

    private ServiceOrder lockCompanyOrder(UUID orderId) {
        return orders.findForUpdateByIdAndServiceCompanyUserId(orderId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
    }

    private List<Technician> validateStaff(UUID orderId, VisitStaffingRequest request) {
        var ids = new HashSet<>(request.technicianIds());
        if (ids.size() != request.technicianIds().size()) {
            throw new FieldValidationException(Map.of("technicianIds", List.of("Wykonawcy nie mogą się powtarzać.")));
        }
        if (!ids.contains(request.coordinatorTechnicianId())) {
            throw new FieldValidationException(Map.of("coordinatorTechnicianId", List.of("Wybierz koordynatora z obsady.")));
        }
        var staff = orders.findEligibleTechnicians(orderId).stream().filter(t -> ids.contains(t.getId())).toList();
        if (staff.size() != ids.size()) {
            throw new FieldValidationException(Map.of("technicianIds", List.of("Wykonawca nie ma dostępu do zlecenia.")));
        }
        return staff;
    }

    private static Technician coordinator(List<Technician> staff, UUID id) {
        return staff.stream().filter(technician -> technician.getId().equals(id)).findFirst().orElseThrow();
    }

    private static VisitTechnician membership(ServiceVisit visit, Technician technician) {
        var membership = new VisitTechnician();
        membership.setId(new VisitTechnicianId(visit.getId(), technician.getId()));
        membership.setVisit(visit);
        membership.setTechnician(technician);
        return membership;
    }
}
