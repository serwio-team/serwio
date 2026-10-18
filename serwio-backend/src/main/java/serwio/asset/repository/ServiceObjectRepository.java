package serwio.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import serwio.asset.model.ServiceObject;
import java.util.Optional;
import java.util.UUID;

public interface ServiceObjectRepository extends JpaRepository<ServiceObject, UUID> {
    boolean existsByLocationIdAndSerialNumber(UUID locationId, String serialNumber);

    @EntityGraph(attributePaths = "location")
    Page<ServiceObject> findAllByActiveTrueAndLocationAssetOwnerUserId(UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Page<ServiceObject> findAllByActiveTrueAndLocationIdAndLocationAssetOwnerUserId(
            UUID locationId, UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Optional<ServiceObject> findByIdAndActiveTrueAndLocationAssetOwnerUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceObject> findForUpdateByIdAndActiveTrueAndLocationAssetOwnerUserId(UUID id, UUID userId);
}
