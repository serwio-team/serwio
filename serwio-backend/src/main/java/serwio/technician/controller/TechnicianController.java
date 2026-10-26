package serwio.technician.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import serwio.technician.dto.TechnicianCreateRequest;
import serwio.technician.dto.TechnicianPasswordRequest;
import serwio.technician.dto.TechnicianProfileResponse;
import serwio.technician.dto.TechnicianResponse;
import serwio.technician.dto.TechnicianUpdateRequest;
import serwio.technician.service.TechnicianService;

@RestController
@RequestMapping("/api/technicians")
@RequiredArgsConstructor
public class TechnicianController {
    private final TechnicianService service;

    @GetMapping
    public List<TechnicianResponse> list(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(branchId, page, size);
    }

    @GetMapping("/me")
    public TechnicianProfileResponse current() {
        return service.getCurrent();
    }

    @GetMapping("/{id}")
    public TechnicianResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<TechnicianResponse> create(@Valid @RequestBody TechnicianCreateRequest request) {
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/technicians/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public TechnicianResponse update(@PathVariable UUID id, @Valid @RequestBody TechnicianUpdateRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable UUID id,
                                               @Valid @RequestBody TechnicianPasswordRequest request) {
        service.changePassword(id, request);
        return ResponseEntity.noContent().build();
    }
}
