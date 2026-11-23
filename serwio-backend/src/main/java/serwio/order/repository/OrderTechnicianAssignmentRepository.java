package serwio.order.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.OrderTechnicianAssignment;

public interface OrderTechnicianAssignmentRepository extends JpaRepository<OrderTechnicianAssignment, UUID> {
    @EntityGraph(attributePaths = {"technician", "technician.branch"})
    List<OrderTechnicianAssignment> findAllByServiceOrderIdAndUnassignedAtIsNull(UUID serviceOrderId);
}
