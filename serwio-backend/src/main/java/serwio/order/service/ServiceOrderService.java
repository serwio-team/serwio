package serwio.order.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import serwio.account.repository.UserAccountRepository;
import serwio.asset.repository.ServiceObjectRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.order.dto.*;
import serwio.order.model.*;
import serwio.order.repository.OrderStatusHistoryRepository;
import serwio.order.repository.ServiceOrderRepository;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceOrderService {
    private final ServiceOrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final ServiceObjectRepository objects;
    private final ServiceCompanyRepository companies;
    private final UserAccountRepository accounts;
    private final CurrentUser currentUser;

    @Transactional
    public ServiceOrderResponse create(ServiceOrderRequest request) {
        var userId = currentUser.id();
        var account = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika."));
        if (!"ASSET_OWNER".equals(account.getRole())) throw new AccessDeniedException("Zlecenie może utworzyć tylko właściciel obiektu.");
        var object = objects.findForUpdateByIdAndActiveTrueAndLocationAssetOwnerUserId(request.serviceObjectId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono aktywnego obiektu serwisowego: " + request.serviceObjectId()));
        var availableCompanies = companies.findAll(PageRequest.of(0, 2)).getContent();
        if (availableCompanies.size() != 1) {
            org.slf4j.LoggerFactory.getLogger(ServiceOrderService.class).error("Tworzenie zlecenia wymaga dokładnie jednej firmy serwisowej.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Firma serwisowa nie jest poprawnie skonfigurowana.");
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var order = new ServiceOrder();
        order.setServiceObject(object);
        order.setServiceCompany(availableCompanies.getFirst());
        order.setCreatedByUser(account);
        order.setOrderType(request.orderType().name());
        order.setDescription(request.description().strip());
        order.setCurrentStatus(OrderStatus.NEW.name());
        order.setCreatedAt(now);
        orders.save(order);
        var entry = new OrderStatusHistory();
        entry.setServiceOrder(order);
        entry.setStatus(OrderStatus.NEW.name());
        entry.setChangedByUser(account);
        entry.setChangedAt(now);
        history.save(entry);
        return response(order);
    }

    public List<ServiceOrderListItemResponse> list(OrderStatus status, int page, int size) {
        var userId = currentUser.id();
        var role = role(userId);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        var filter = status == null ? null : status.name();
        var result = "ASSET_OWNER".equals(role) ? orders.findForOwner(userId, filter, pageable)
                : orders.findForCompany(userId, filter, pageable);
        return result.getContent().stream().map(order -> new ServiceOrderListItemResponse(order.getId(),
                order.getServiceObject().getId(), order.getServiceObject().getName(),
                order.getServiceObject().getLocation().getId(), order.getServiceObject().getLocation().getName(),
                order.getOrderType(), order.getCurrentStatus(), order.getCreatedAt())).toList();
    }

    public ServiceOrderResponse get(UUID id) {
        var userId = currentUser.id();
        var role = role(userId);
        var result = "ASSET_OWNER".equals(role) ? orders.findByIdAndServiceObjectLocationAssetOwnerUserId(id, userId)
                : orders.findByIdAndServiceCompanyUserId(id, userId);
        return response(result.orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zlecenia: " + id)));
    }

    private String role(UUID userId) {
        var role = accounts.findById(userId).orElseThrow(() -> new AccessDeniedException("Brak konta użytkownika.")).getRole();
        if (!"ASSET_OWNER".equals(role) && !"SERVICE_COMPANY".equals(role))
            throw new AccessDeniedException("Brak dostępu do zleceń.");
        return role;
    }

    private ServiceOrderResponse response(ServiceOrder order) {
        var object = order.getServiceObject();
        var location = object.getLocation();
        var owner = location.getAssetOwner();
        return new ServiceOrderResponse(order.getId(), object.getId(), object.getName(),
                location.getId(), location.getName(), location.getAddressLine(), location.getCity(), location.getTimezone(),
                owner.getId(), owner.getName(), order.getServiceCompany().getId(), order.getOrderType(),
                order.getDescription(), order.getCurrentStatus(), order.getCreatedByUser().getId(), order.getCreatedAt(), order.getClosedAt());
    }
}
