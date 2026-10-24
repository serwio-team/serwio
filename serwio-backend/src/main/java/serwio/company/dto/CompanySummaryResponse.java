package serwio.company.dto;

public record CompanySummaryResponse(
        long activeBranchCount,
        long inactiveBranchCount,
        long activeTechnicianCount,
        long pendingTechnicianCount,
        long inactiveTechnicianCount
) {}
