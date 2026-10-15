package serwio.visit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.ServiceVisit;
import java.util.UUID;

public interface ServiceVisitRepository extends JpaRepository<ServiceVisit, UUID> {
}
