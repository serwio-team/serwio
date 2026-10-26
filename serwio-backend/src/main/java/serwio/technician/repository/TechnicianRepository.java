package serwio.technician.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import serwio.technician.model.Technician;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {
    Optional<Technician> findByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "branch"})
    Page<Technician> findAllByBranchServiceCompanyUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "branch"})
    Page<Technician> findAllByBranchIdAndBranchServiceCompanyUserId(UUID branchId, UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "branch"})
    Optional<Technician> findByIdAndBranchServiceCompanyUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Technician> findForUpdateByIdAndBranchServiceCompanyUserId(UUID id, UUID userId);

    long countByBranchIdAndActiveTrue(UUID branchId);

    long countByBranchServiceCompanyIdAndActive(UUID serviceCompanyId, boolean active);

    long countByBranchServiceCompanyIdAndActiveTrueAndUserPasswordChangeRequiredTrue(UUID serviceCompanyId);

    @Query("""
            select t.branch.id as branchId, count(t) as technicianCount
            from Technician t
            where t.branch.id in :branchIds and t.active = true
            group by t.branch.id""")
    List<BranchTechnicianCount> countActiveByBranchIds(@Param("branchIds") Collection<UUID> branchIds);

    interface BranchTechnicianCount {
        UUID getBranchId();

        long getTechnicianCount();
    }
}
