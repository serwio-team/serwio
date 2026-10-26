package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import serwio.company.model.ServiceCompany;
import java.util.UUID;
import java.util.Optional;

public interface ServiceCompanyRepository extends JpaRepository<ServiceCompany, UUID> {
    Optional<ServiceCompany> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceCompany> findForUpdateByUserId(UUID userId);
}
