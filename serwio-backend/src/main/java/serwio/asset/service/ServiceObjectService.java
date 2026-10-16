package serwio.asset.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.asset.dto.ServiceObjectListItemResponse;
import serwio.asset.dto.ServiceObjectPageResponse;
import serwio.asset.dto.ServiceObjectRequest;
import serwio.asset.dto.ServiceObjectResponse;
import serwio.asset.model.ServiceObject;
import serwio.asset.repository.ServiceObjectRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.location.repository.LocationRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceObjectService {
    private final ServiceObjectRepository objects;
    private final LocationRepository locations;

    public ServiceObjectPageResponse list(UUID locationId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        var result = locationId == null
                ? objects.findAllByActiveTrue(pageable)
                : objects.findAllByActiveTrueAndLocationId(locationId, pageable);
        return new ServiceObjectPageResponse(result.getContent().stream().map(this::toListItemResponse).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public ServiceObjectResponse get(UUID id) {
        return toResponse(objects.findByIdAndActiveTrue(id).orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public ServiceObjectResponse create(ServiceObjectRequest request) {
        var object = new ServiceObject();
        applyRequest(object, request);
        object.setActive(true);
        object.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(objects.save(object));
    }

    @Transactional
    public ServiceObjectResponse update(UUID id, ServiceObjectRequest request) {
        var object = objects.findForUpdateByIdAndActiveTrue(id).orElseThrow(() -> notFound(id));
        applyRequest(object, request);
        return toResponse(object);
    }

    @Transactional
    public void delete(UUID id) {
        var object = objects.findForUpdateByIdAndActiveTrue(id).orElseThrow(() -> notFound(id));
        object.setActive(false);
    }

    private void applyRequest(ServiceObject object, ServiceObjectRequest request) {
        var location = locations.findById(request.locationId()).orElseThrow(() ->
                new ResourceNotFoundException("Nie znaleziono lokalizacji: " + request.locationId()));
        object.setLocation(location);
        object.setName(request.name().strip());
        object.setType(request.type().strip());
        object.setDescription(request.description());
        object.setManufacturer(request.manufacturer());
        object.setModel(request.model());
        object.setSerialNumber(request.serialNumber());
        object.setAttributes(request.attributes());
    }

    private ServiceObjectListItemResponse toListItemResponse(ServiceObject object) {
        return new ServiceObjectListItemResponse(object.getId(), object.getName(), object.getType(),
                object.getLocation().getId(), object.getLocation().getName());
    }

    private ServiceObjectResponse toResponse(ServiceObject object) {
        return new ServiceObjectResponse(object.getId(), object.getLocation().getId(),
                object.getLocation().getName(), object.getName(), object.getType(), object.getDescription(),
                object.getManufacturer(), object.getModel(), object.getSerialNumber(), object.getAttributes(),
                object.getPublicId(), object.getQrGeneratedAt(), Boolean.TRUE.equals(object.getActive()),
                object.getCreatedAt());
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono aktywnego obiektu serwisowego: " + id);
    }
}
