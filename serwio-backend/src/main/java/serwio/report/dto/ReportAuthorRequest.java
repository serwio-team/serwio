package serwio.report.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReportAuthorRequest(@NotNull UUID technicianId) {}
