package serwio.order.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import serwio.order.dto.*;
import serwio.order.model.OrderStatus;
import serwio.order.service.ServiceOrderService;

@RestController
@RequestMapping("/api/service-orders")
@RequiredArgsConstructor
public class ServiceOrderController {
    private final ServiceOrderService service;

    @PostMapping
    public ResponseEntity<ServiceOrderResponse> create(@Valid @RequestBody ServiceOrderRequest request) {
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/service-orders/" + created.id())).body(created);
    }

    @GetMapping
    public List<ServiceOrderListItemResponse> list(@RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    @GetMapping("/{id}")
    public ServiceOrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }
}
