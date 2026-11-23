package serwio.order.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.OrderBranchAssignment;

public interface OrderBranchAssignmentRepository extends JpaRepository<OrderBranchAssignment, UUID> {
    @EntityGraph(attributePaths = "branch")
    List<OrderBranchAssignment> findAllByServiceOrderIdAndUnassignedAtIsNull(UUID serviceOrderId);
}
