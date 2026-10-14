package serwio.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.location.model.LocationAvailability;
import java.util.UUID;

public interface LocationAvailabilityRepository extends JpaRepository<LocationAvailability, UUID> {
}
