package serwio.report.dto;

import jakarta.validation.constraints.Size;

import static serwio.report.model.ServiceReport.MAX_TEXT_LENGTH;

public record ReportDraftRequest(
        @Size(max = MAX_TEXT_LENGTH) String actionsDone,
        @Size(max = MAX_TEXT_LENGTH) String diagnosis,
        @Size(max = MAX_TEXT_LENGTH) String resolution,
        @Size(max = MAX_TEXT_LENGTH) String usedMaterials,
        @Size(max = MAX_TEXT_LENGTH) String recommendations) {}
