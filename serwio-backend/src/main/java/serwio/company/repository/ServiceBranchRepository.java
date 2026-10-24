package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import serwio.company.model.ServiceBranch;
import java.util.UUID;
import java.util.Optional;

public interface ServiceBranchRepository extends JpaRepository<ServiceBranch, UUID> {
    Optional<ServiceBranch> findFirstByServiceCompanyIdAndNameOrderByCreatedAtAscIdAsc(UUID serviceCompanyId,
                                                                                       String name);

    Page<ServiceBranch> findAllByServiceCompanyUserId(UUID userId, Pageable pageable);

    Optional<ServiceBranch> findByIdAndServiceCompanyUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceBranch> findForUpdateByIdAndServiceCompanyUserId(UUID id, UUID userId);

    boolean existsByServiceCompanyIdAndNameIgnoreCase(UUID serviceCompanyId, String name);

    boolean existsByServiceCompanyIdAndNameIgnoreCaseAndIdNot(UUID serviceCompanyId, String name, UUID id);

    long countByServiceCompanyIdAndActiveTrue(UUID serviceCompanyId);

    long countByServiceCompanyIdAndActive(UUID serviceCompanyId, boolean active);
}
