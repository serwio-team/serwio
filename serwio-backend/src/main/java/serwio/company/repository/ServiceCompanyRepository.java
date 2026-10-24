package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.company.model.ServiceCompany;
import java.util.UUID;
import java.util.Optional;

public interface ServiceCompanyRepository extends JpaRepository<ServiceCompany, UUID> {
    Optional<ServiceCompany> findByUserId(UUID userId);
}
