package serwio.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.asset.model.CompanyObjectService;
import java.util.UUID;

public interface CompanyObjectServiceRepository extends JpaRepository<CompanyObjectService, UUID> {
}
