package serwio.order.dto;

import java.util.List;
import java.util.UUID;

public record OrderAssignmentResponse(
        UUID serviceOrderId, String status, long version,
        List<BranchItem> branches, List<TechnicianItem> technicians, UUID coordinatorTechnicianId) {
    public record BranchItem(UUID id, String name) {}

    public record TechnicianItem(UUID id, String displayName, UUID branchId) {}
}
