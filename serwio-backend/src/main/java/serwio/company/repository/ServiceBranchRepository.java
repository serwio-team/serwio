package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.company.model.ServiceBranch;
import java.util.UUID;

public interface ServiceBranchRepository extends JpaRepository<ServiceBranch, UUID> {
}
