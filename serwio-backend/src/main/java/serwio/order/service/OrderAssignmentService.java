package serwio.order.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.repository.UserAccountRepository;
import serwio.company.model.ServiceBranch;
import serwio.company.repository.ServiceBranchRepository;
import serwio.exception.ConflictException;
import serwio.exception.FieldValidationException;
import serwio.exception.ResourceNotFoundException;
import serwio.order.dto.OrderAssignmentRequest;
import serwio.order.dto.OrderAssignmentResponse;
import serwio.order.model.*;
import serwio.order.repository.*;
import serwio.security.CurrentUser;
import serwio.technician.model.Technician;
import serwio.technician.repository.TechnicianRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAssignmentService {
    private final ServiceOrderRepository orders;
    private final OrderBranchAssignmentRepository branchAssignments;
    private final OrderTechnicianAssignmentRepository technicianAssignments;
    private final OrderStatusHistoryRepository history;
    private final ServiceBranchRepository branches;
    private final TechnicianRepository technicians;
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;

    public OrderAssignmentResponse get(UUID orderId) {
        var order = orders.findByIdAndServiceCompanyUserId(orderId, currentUser.id())
                .orElseThrow(() -> notFound(orderId));
        return response(order, activeBranches(order), activeTechnicians(order));
    }

    @Transactional
    public OrderAssignmentResponse replace(UUID orderId, OrderAssignmentRequest request) {
        var userId = currentUser.id();
        var branchIds = distinct("branchIds", request.branchIds());
        var technicianIds = distinct("technicianIds", request.technicianIds());
        if (branchIds.isEmpty() && technicianIds.isEmpty()) {
            throw invalid("branchIds", "Wybierz co najmniej jeden oddział lub serwisanta.");
        }
        var order = orders.findForUpdateByIdAndServiceCompanyUserId(orderId, userId)
                .orElseThrow(() -> notFound(orderId));
        var status = order.getCurrentStatus();
        if (!OrderStatus.NEW.name().equals(status) && !OrderStatus.ASSIGNED.name().equals(status)
                && !OrderStatus.REVISIT_REQUIRED.name().equals(status)
                && !OrderStatus.AWAITING_SCHEDULING.name().equals(status)) {
            throw new ConflictException("Przydział można zmienić przed potwierdzeniem terminu wizyty.");
        }
        if (!request.expectedVersion().equals(order.getAssignmentVersion())) {
            throw new ConflictException("Przydział został zmieniony. Odśwież dane i spróbuj ponownie.");
        }
        var companyId = order.getServiceCompany().getId();
        var selectedBranches = branches.findAllByIdInAndServiceCompanyIdAndActiveTrue(branchIds, companyId);
        if (selectedBranches.size() != branchIds.size()) {
            throw invalid("branchIds", "Oddział jest nieaktywny lub nie należy do firmy.");
        }
        var wantedTechnicianIds = new HashSet<>(technicianIds);
        wantedTechnicianIds.add(request.coordinatorTechnicianId());
        var found = technicians.findAllByIdInAndBranchServiceCompanyIdAndActiveTrueAndUserStatus(
                wantedTechnicianIds, companyId, "ACTIVE");
        var foundIds = found.stream().map(Technician::getId).collect(Collectors.toSet());
        if (!foundIds.containsAll(technicianIds)) {
            throw invalid("technicianIds", "Serwisant jest nieaktywny lub nie należy do firmy.");
        }
        var coordinator = found.stream().filter(t -> t.getId().equals(request.coordinatorTechnicianId()))
                .findFirst().orElseThrow(() -> invalid("coordinatorTechnicianId",
                        "Koordynator jest nieaktywny lub nie należy do firmy."));
        if (!technicianIds.contains(coordinator.getId()) && !branchIds.contains(coordinator.getBranch().getId())) {
            throw invalid("coordinatorTechnicianId", "Koordynator musi być objęty przydziałem.");
        }

        var currentBranches = activeBranchRows(order);
        var currentTechnicians = activeTechnicianRows(order);
        var sameCoordinator = order.getCoordinatorTechnician() != null
                && order.getCoordinatorTechnician().getId().equals(coordinator.getId());
        var sameBranches = currentBranches.stream().map(a -> a.getBranch().getId())
                .collect(Collectors.toSet()).equals(new HashSet<>(branchIds));
        var sameTechnicians = currentTechnicians.stream().map(a -> a.getTechnician().getId())
                .collect(Collectors.toSet()).equals(new HashSet<>(technicianIds));
        if (sameCoordinator && sameBranches && sameTechnicians) {
            return response(order, currentBranches.stream().map(OrderBranchAssignment::getBranch).toList(),
                    currentTechnicians.stream().map(OrderTechnicianAssignment::getTechnician).toList());
        }

        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var actor = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika."));
        for (var row : currentBranches) {
            if (!branchIds.contains(row.getBranch().getId())) row.setUnassignedAt(now);
        }
        for (var row : currentTechnicians) {
            if (!technicianIds.contains(row.getTechnician().getId())) row.setUnassignedAt(now);
        }
        var keptBranches = currentBranches.stream().map(a -> a.getBranch().getId()).collect(Collectors.toSet());
        for (var branch : selectedBranches) {
            if (keptBranches.contains(branch.getId())) continue;
            var row = new OrderBranchAssignment();
            row.setServiceOrder(order);
            row.setBranch(branch);
            row.setAssignedByUser(actor);
            row.setAssignedAt(now);
            branchAssignments.save(row);
        }
        var keptTechnicians = currentTechnicians.stream().map(a -> a.getTechnician().getId()).collect(Collectors.toSet());
        var selectedTechnicians = found.stream().filter(t -> technicianIds.contains(t.getId())).toList();
        for (var technician : selectedTechnicians) {
            if (keptTechnicians.contains(technician.getId())) continue;
            var row = new OrderTechnicianAssignment();
            row.setServiceOrder(order);
            row.setTechnician(technician);
            row.setAssignedByUser(actor);
            row.setAssignedAt(now);
            technicianAssignments.save(row);
        }
        order.setCoordinatorTechnician(coordinator);
        order.setAssignmentVersion(order.getAssignmentVersion() + 1);
        if (OrderStatus.NEW.name().equals(status)) {
            order.setCurrentStatus(OrderStatus.ASSIGNED.name());
            var entry = new OrderStatusHistory();
            entry.setServiceOrder(order);
            entry.setStatus(OrderStatus.ASSIGNED.name());
            entry.setChangedByUser(actor);
            entry.setChangedAt(now);
            history.save(entry);
        }
        orders.save(order);
        return response(order, selectedBranches, selectedTechnicians);
    }

    private List<OrderBranchAssignment> activeBranchRows(ServiceOrder order) {
        return branchAssignments.findAllByServiceOrderIdAndUnassignedAtIsNull(order.getId());
    }

    private List<OrderTechnicianAssignment> activeTechnicianRows(ServiceOrder order) {
        return technicianAssignments.findAllByServiceOrderIdAndUnassignedAtIsNull(order.getId());
    }

    private List<ServiceBranch> activeBranches(ServiceOrder order) {
        return activeBranchRows(order).stream().map(OrderBranchAssignment::getBranch).toList();
    }

    private List<Technician> activeTechnicians(ServiceOrder order) {
        return activeTechnicianRows(order).stream().map(OrderTechnicianAssignment::getTechnician).toList();
    }

    private OrderAssignmentResponse response(ServiceOrder order, List<ServiceBranch> branchList,
                                             List<Technician> technicianList) {
        var coordinator = order.getCoordinatorTechnician();
        return new OrderAssignmentResponse(order.getId(), order.getCurrentStatus(), order.getAssignmentVersion(),
                branchList.stream().sorted(Comparator.comparing(ServiceBranch::getName).thenComparing(ServiceBranch::getId))
                        .map(b -> new OrderAssignmentResponse.BranchItem(b.getId(), b.getName())).toList(),
                technicianList.stream().sorted(Comparator.comparing(Technician::getDisplayName).thenComparing(Technician::getId))
                        .map(t -> new OrderAssignmentResponse.TechnicianItem(t.getId(), t.getDisplayName(), t.getBranch().getId())).toList(),
                coordinator == null ? null : coordinator.getId());
    }

    private static List<UUID> distinct(String field, List<UUID> ids) {
        if (new HashSet<>(ids).size() != ids.size()) throw invalid(field, "Identyfikatory nie mogą się powtarzać.");
        return List.copyOf(ids);
    }

    private static FieldValidationException invalid(String field, String message) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        errors.put(field, new ArrayList<>(List.of(message)));
        return new FieldValidationException(errors);
    }

    private static ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono zlecenia: " + id);
    }
}
