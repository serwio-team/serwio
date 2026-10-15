package serwio.company.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.company.model.ServiceCompany;
import java.util.UUID;

public interface ServiceCompanyRepository extends JpaRepository<ServiceCompany, UUID> {
}
