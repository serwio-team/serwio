package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.OrderTechnicianAssignment;
import java.util.UUID;

public interface OrderTechnicianAssignmentRepository extends JpaRepository<OrderTechnicianAssignment, UUID> {
}
