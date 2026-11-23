package serwio.report.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import serwio.report.model.ServiceReport;

public record ServiceReportResponse(UUID id, UUID serviceOrderId, UUID authorTechnicianId,
                                    String actionsDone, String diagnosis, String resolution,
                                    String usedMaterials, String recommendations,
                                    OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime finalizedAt) {

    public static ServiceReportResponse from(ServiceReport report) {
        return new ServiceReportResponse(report.getId(), report.getServiceOrder().getId(),
                report.getAuthorTechnician().getId(), report.getActionsDone(), report.getDiagnosis(),
                report.getResolution(), report.getUsedMaterials(), report.getRecommendations(),
                report.getCreatedAt(), report.getUpdatedAt(), report.getFinalizedAt());
    }
}
