package serwio.asset.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.asset.model.ServiceObjectFile;
import java.util.UUID;

public interface ServiceObjectFileRepository extends JpaRepository<ServiceObjectFile, UUID> {
}
