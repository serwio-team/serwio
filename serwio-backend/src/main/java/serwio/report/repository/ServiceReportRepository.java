package serwio.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.report.model.ServiceReport;
import java.util.UUID;

public interface ServiceReportRepository extends JpaRepository<ServiceReport, UUID> {
}
