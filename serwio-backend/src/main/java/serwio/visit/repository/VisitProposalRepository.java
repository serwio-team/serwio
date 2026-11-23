package serwio.visit.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.VisitProposal;
import java.util.UUID;

public interface VisitProposalRepository extends JpaRepository<VisitProposal, UUID> {
    Optional<VisitProposal> findFirstByVisitIdAndStatusOrderByCreatedAtDescIdAsc(UUID visitId, String status);

    Optional<VisitProposal> findByIdAndVisitIdAndStatus(UUID proposalId, UUID visitId, String status);
}
