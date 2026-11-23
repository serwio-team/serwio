package serwio.report.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.report.model.ServiceReport;

public interface ServiceReportRepository extends JpaRepository<ServiceReport, UUID> {
    Optional<ServiceReport> findByServiceOrderId(UUID serviceOrderId);

    boolean existsByServiceOrderIdAndFinalizedAtIsNotNull(UUID serviceOrderId);
}
