package serwio.visit.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.exception.ResourceNotFoundException;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;
import serwio.visit.dto.VisitTechnicianResponse;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceVisitService {
    private final ServiceOrderRepository orders;
    private final CurrentUser currentUser;

    public List<VisitTechnicianResponse> candidates(UUID orderId) {
        orders.findByIdAndServiceCompanyUserId(orderId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + orderId));
        return orders.findEligibleTechnicians(orderId).stream().map(VisitTechnicianResponse::from).toList();
    }
}
