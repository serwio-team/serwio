package serwio.visit.model;

import serwio.order.model.OrderStatus;

public enum VisitOutcome {
    COMPLETED(OrderStatus.COMPLETED),
    REVISIT_REQUIRED(OrderStatus.REVISIT_REQUIRED);

    private final OrderStatus orderStatus;

    VisitOutcome(OrderStatus orderStatus) {
        this.orderStatus = orderStatus;
    }

    public OrderStatus orderStatus() {
        return orderStatus;
    }
}
