package serwio.technician.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.technician.model.Technician;
import java.util.UUID;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, UUID> {
    Optional<Technician> findByUserId(UUID userId);
}
