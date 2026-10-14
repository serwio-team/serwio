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
    @EntityGraph(attributePaths = "location")
    Page<ServiceObject> findAllByActiveTrue(Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Page<ServiceObject> findAllByActiveTrueAndLocationId(UUID locationId, Pageable pageable);

    @EntityGraph(attributePaths = "location")
    Optional<ServiceObject> findByIdAndActiveTrue(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceObject> findForUpdateByIdAndActiveTrue(UUID id);
}
