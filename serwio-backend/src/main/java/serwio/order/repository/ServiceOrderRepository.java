package serwio.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import serwio.order.model.ServiceOrder;
import java.util.UUID;

public interface ServiceOrderRepository extends JpaRepository<ServiceOrder, UUID> {
}
