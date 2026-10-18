package serwio.location.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.exception.ResourceNotFoundException;
import serwio.location.dto.LocationListItemResponse;
import serwio.location.dto.LocationRequest;
import serwio.location.dto.LocationResponse;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.owner.repository.AssetOwnerRepository;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {
    private final LocationRepository locations;
    private final AssetOwnerRepository owners;
    private final CurrentUser currentUser;

    public List<LocationListItemResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(
                Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        var result = locations.findAllByAssetOwnerUserId(currentUser.id(), pageable);
        return result.getContent().stream()
                .map(location -> new LocationListItemResponse(location.getId(), location.getName(),
                        location.getAddressLine(), location.getCity()))
                .toList();
    }

    public LocationResponse get(UUID id) {
        return toResponse(locations.findByIdAndAssetOwnerUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public LocationResponse create(LocationRequest request) {
        var location = new Location();
        location.setAssetOwner(owners.findByUserId(currentUser.id()).orElseThrow(() ->
                new AccessDeniedException("Konto nie ma profilu właściciela")));
        applyRequest(location, request);
        location.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(locations.save(location));
    }

    @Transactional
    public LocationResponse update(UUID id, LocationRequest request) {
        var location = locations.findForUpdateByIdAndAssetOwnerUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        applyRequest(location, request);
        return toResponse(location);
    }

    @Transactional
    public void delete(UUID id) {
        var location = locations.findForUpdateByIdAndAssetOwnerUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        locations.delete(location);
        locations.flush();
    }

    private void applyRequest(Location location, LocationRequest request) {
        location.setName(request.name().strip());
        location.setAddressLine(request.addressLine().strip());
        location.setCity(request.city().strip());
        location.setPostalCode(request.postalCode().strip());
        location.setCountry(request.country());
        location.setTimezone(request.timezone());
    }

    private LocationResponse toResponse(Location location) {
        return new LocationResponse(location.getId(), location.getAssetOwner().getId(), location.getName(),
                location.getAddressLine(), location.getCity(), location.getPostalCode(),
                location.getCountry(), location.getTimezone());
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono lokalizacji: " + id);
    }
}
