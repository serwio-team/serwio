package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.ServiceOrder;
import serwio.technician.model.Technician;
import java.util.List;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {
    String ELIGIBLE_TECHNICIAN = """
            o.closedAt is null and t.user.status = 'ACTIVE'
            and t.active = true and t.branch.active = true
            and t.branch.serviceCompany = o.serviceCompany
            and (
                exists (select 1 from OrderTechnicianAssignment a
                    where a.serviceOrder = o and a.technician = t and a.unassignedAt is null)
                or exists (select 1 from OrderBranchAssignment a
                    where a.serviceOrder = o and a.branch = t.branch and a.unassignedAt is null)
            )
            """;
    String TECHNICIAN_ACCESS = "exists (select 1 from Technician t where t.user.id = :userId and "
            + ELIGIBLE_TECHNICIAN + ")";

    @Query("select t from Technician t, ServiceOrder o where o.id = :orderId and "
            + ELIGIBLE_TECHNICIAN + " order by t.displayName, t.id")
    List<Technician> findEligibleTechnicians(UUID orderId);

    @EntityGraph(attributePaths = {"serviceObject.location"})
    @Query("select o from ServiceOrder o where o.serviceObject.location.assetOwner.user.id = :userId and (:status is null or o.currentStatus = :status)")
    Page<ServiceOrder> findForOwner(UUID userId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceObject.location"})
    @Query("select o from ServiceOrder o where o.serviceCompany.user.id = :userId and (:status is null or o.currentStatus = :status)")
    Page<ServiceOrder> findForCompany(UUID userId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"serviceObject.location"})
    @Query("select o from ServiceOrder o where (:status is null or o.currentStatus = :status) and "
            + TECHNICIAN_ACCESS)
    Page<ServiceOrder> findForTechnician(UUID userId, String status, Pageable pageable);

    Optional<ServiceOrder> findByIdAndServiceObjectLocationAssetOwnerUserId(UUID id, UUID userId);
    Optional<ServiceOrder> findByIdAndServiceCompanyUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"serviceObject.location", "coordinatorTechnician"})
    @Query("select o from ServiceOrder o where o.id = :id and " + TECHNICIAN_ACCESS)
    Optional<ServiceOrder> findByIdForTechnician(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceOrder> findForUpdateByIdAndServiceCompanyUserId(UUID id, UUID userId);
}
