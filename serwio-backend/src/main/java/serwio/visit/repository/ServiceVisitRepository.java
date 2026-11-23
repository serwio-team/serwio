package serwio.visit.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.visit.model.ServiceVisit;
import java.util.UUID;

public interface ServiceVisitRepository extends JpaRepository<ServiceVisit, UUID> {
    List<ServiceVisit> findAllByServiceOrderIdOrderByCreatedAtDescIdAsc(UUID orderId);

    boolean existsByServiceOrderIdAndStatusNotIn(UUID orderId, List<String> statuses);

    Optional<ServiceVisit> findByIdAndServiceOrderId(UUID visitId, UUID orderId);
}
