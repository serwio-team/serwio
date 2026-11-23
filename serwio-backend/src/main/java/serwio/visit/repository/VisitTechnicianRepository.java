package serwio.visit.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.VisitTechnician;
import serwio.visit.model.VisitTechnicianId;

public interface VisitTechnicianRepository extends JpaRepository<VisitTechnician, VisitTechnicianId> {
    @EntityGraph(attributePaths = "technician")
    List<VisitTechnician> findAllByVisitIdOrderByTechnicianDisplayNameAscTechnicianIdAsc(UUID visitId);
}
