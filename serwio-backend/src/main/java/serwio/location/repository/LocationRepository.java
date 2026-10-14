package serwio.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.location.model.Location;
import java.util.UUID;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findFirstByAssetOwnerIdAndNameOrderByCreatedAtAscIdAsc(UUID assetOwnerId, String name);
}
