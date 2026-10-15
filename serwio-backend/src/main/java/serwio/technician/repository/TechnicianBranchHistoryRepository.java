package serwio.technician.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.technician.model.TechnicianBranchHistory;
import java.util.UUID;

public interface TechnicianBranchHistoryRepository extends JpaRepository<TechnicianBranchHistory, UUID> {
}
