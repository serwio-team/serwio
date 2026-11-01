package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.ServiceOrder;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {
    @EntityGraph(attributePaths = {"serviceObject.location"})
    @Query("select o from ServiceOrder o where o.serviceObject.location.assetOwner.user.id = :userId and (:status is null or o.currentStatus = :status)")
    Page<ServiceOrder> findForOwner(UUID userId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceObject.location"})
    @Query("select o from ServiceOrder o where o.serviceCompany.user.id = :userId and (:status is null or o.currentStatus = :status)")
    Page<ServiceOrder> findForCompany(UUID userId, String status, Pageable pageable);

    Optional<ServiceOrder> findByIdAndServiceObjectLocationAssetOwnerUserId(UUID id, UUID userId);
    Optional<ServiceOrder> findByIdAndServiceCompanyUserId(UUID id, UUID userId);
}
