package serwio.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.report.model.ServiceReportFile;
import java.util.UUID;

public interface ServiceReportFileRepository extends JpaRepository<ServiceReportFile, UUID> {
}
