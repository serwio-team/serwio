package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.OrderStatusHistory;
import java.util.UUID;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, UUID> {
}
