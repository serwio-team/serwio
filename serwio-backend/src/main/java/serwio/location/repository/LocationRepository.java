package serwio.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import serwio.location.model.Location;
import java.util.UUID;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, UUID> {
    Optional<Location> findFirstByAssetOwnerIdAndNameOrderByCreatedAtAscIdAsc(UUID assetOwnerId, String name);

    Page<Location> findAllByAssetOwnerUserId(UUID userId, Pageable pageable);

    Optional<Location> findByIdAndAssetOwnerUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Location> findForUpdateByIdAndAssetOwnerUserId(UUID id, UUID userId);
}
