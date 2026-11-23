package serwio.report.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.report.model.ReportAuthorHistory;

public interface ReportAuthorHistoryRepository extends JpaRepository<ReportAuthorHistory, UUID> {}
