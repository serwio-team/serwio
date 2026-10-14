package serwio.visit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.VisitTechnician;
import serwio.visit.model.VisitTechnicianId;

public interface VisitTechnicianRepository extends JpaRepository<VisitTechnician, VisitTechnicianId> {
}
