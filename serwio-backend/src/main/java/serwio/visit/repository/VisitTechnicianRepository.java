package serwio.visit.repository;

import java.util.List;
import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.OffsetDateTime;
import serwio.visit.model.VisitTechnician;
import serwio.visit.model.VisitTechnicianId;

public interface VisitTechnicianRepository extends JpaRepository<VisitTechnician, VisitTechnicianId> {
    @EntityGraph(attributePaths = "technician")
    List<VisitTechnician> findAllByVisitIdOrderByTechnicianDisplayNameAscTechnicianIdAsc(UUID visitId);

    @Query("select vt.id.technicianId from VisitTechnician vt where vt.id.visitId = :visitId order by vt.id.technicianId")
    List<UUID> findTechnicianIds(UUID visitId);

    @Query("""
            select count(vt) > 0 from VisitTechnician vt
            where vt.technician.id in :technicianIds and vt.visit.status in :statuses
                and vt.visit.startsAt < :endsAt and vt.visit.endsAt > :startsAt
            """)
    boolean hasOverlap(Collection<UUID> technicianIds, List<String> statuses,
                       OffsetDateTime startsAt, OffsetDateTime endsAt);
}
