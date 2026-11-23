package serwio.visit.dto;

import jakarta.validation.constraints.NotNull;
import serwio.visit.model.VisitOutcome;

public record VisitCompletionRequest(@NotNull VisitOutcome outcome) {}
