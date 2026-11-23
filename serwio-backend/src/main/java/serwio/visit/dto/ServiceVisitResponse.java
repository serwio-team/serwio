package serwio.visit.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import serwio.visit.model.VisitOutcome;

public record ServiceVisitResponse(UUID id, String status, UUID coordinatorTechnicianId,
                                    List<VisitTechnicianResponse> technicians, Proposal currentProposal,
                                    OffsetDateTime startsAt, OffsetDateTime endsAt,
                                    OffsetDateTime startedAt, OffsetDateTime completedAt, VisitOutcome outcome) {

    public record Proposal(UUID id, String proposedBy, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}
