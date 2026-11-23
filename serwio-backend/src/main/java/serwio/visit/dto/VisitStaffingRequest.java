package serwio.visit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record VisitStaffingRequest(@NotEmpty List<@NotNull UUID> technicianIds,
                                   @NotNull UUID coordinatorTechnicianId) {}
