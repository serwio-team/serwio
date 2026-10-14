package serwio.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.file.model.StoredFile;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {
}
