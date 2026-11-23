package serwio.visit.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ServiceVisitResponse(UUID id, String status, UUID coordinatorTechnicianId,
                                    List<VisitTechnicianResponse> technicians, Proposal currentProposal,
                                    OffsetDateTime startsAt, OffsetDateTime endsAt) {

    public record Proposal(UUID id, String proposedBy, OffsetDateTime startsAt, OffsetDateTime endsAt) {}
}
