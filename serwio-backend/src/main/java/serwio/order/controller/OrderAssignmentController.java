package serwio.order.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import serwio.order.dto.OrderAssignmentRequest;
import serwio.order.dto.OrderAssignmentResponse;
import serwio.order.service.OrderAssignmentService;

@RestController
@RequestMapping("/api/service-orders/{id}/assignment")
@RequiredArgsConstructor
public class OrderAssignmentController {
    private final OrderAssignmentService service;

    @GetMapping
    public OrderAssignmentResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping
    public OrderAssignmentResponse replace(@PathVariable UUID id, @Valid @RequestBody OrderAssignmentRequest request) {
        return service.replace(id, request);
    }
}
