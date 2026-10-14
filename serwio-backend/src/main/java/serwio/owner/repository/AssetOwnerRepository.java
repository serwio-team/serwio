package serwio.owner.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.owner.model.AssetOwner;
import java.util.UUID;
import java.util.Optional;

public interface AssetOwnerRepository extends JpaRepository<AssetOwner, UUID> {
    Optional<AssetOwner> findByUserId(UUID userId);
}
