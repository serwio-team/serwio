package serwio.order.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record OrderAssignmentRequest(
        @NotNull Long expectedVersion,
        @NotNull List<@NotNull UUID> branchIds,
        @NotNull List<@NotNull UUID> technicianIds,
        @NotNull UUID coordinatorTechnicianId) {}
