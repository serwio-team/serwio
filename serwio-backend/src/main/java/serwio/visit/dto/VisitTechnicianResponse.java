package serwio.visit.dto;

import java.util.UUID;
import serwio.technician.model.Technician;

public record VisitTechnicianResponse(UUID id, String displayName, UUID branchId) {
    public static VisitTechnicianResponse from(Technician technician) {
        return new VisitTechnicianResponse(technician.getId(), technician.getDisplayName(),
                technician.getBranch().getId());
    }
}
