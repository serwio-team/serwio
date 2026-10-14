package serwio.visit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.VisitProposal;
import java.util.UUID;

public interface VisitProposalRepository extends JpaRepository<VisitProposal, UUID> {
}
