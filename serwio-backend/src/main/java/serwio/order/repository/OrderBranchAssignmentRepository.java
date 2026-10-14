package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.OrderBranchAssignment;
import java.util.UUID;

public interface OrderBranchAssignmentRepository extends JpaRepository<OrderBranchAssignment, UUID> {
}
