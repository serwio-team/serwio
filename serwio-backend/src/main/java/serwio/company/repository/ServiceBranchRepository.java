package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.company.model.ServiceBranch;
import java.util.UUID;
import java.util.Optional;

public interface ServiceBranchRepository extends JpaRepository<ServiceBranch, UUID> {
    Optional<ServiceBranch> findFirstByServiceCompanyIdAndNameOrderByCreatedAtAscIdAsc(UUID serviceCompanyId,
                                                                                       String name);
}
