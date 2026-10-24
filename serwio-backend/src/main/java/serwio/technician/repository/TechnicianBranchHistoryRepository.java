package serwio.technician.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.technician.model.TechnicianBranchHistory;
import java.util.List;
import java.util.UUID;

public interface TechnicianBranchHistoryRepository extends JpaRepository<TechnicianBranchHistory, UUID> {
    List<TechnicianBranchHistory> findAllByTechnicianIdAndValidToIsNull(UUID technicianId);
}
