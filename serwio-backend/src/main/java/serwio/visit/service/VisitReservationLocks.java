package serwio.visit.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.technician.repository.TechnicianRepository;
import serwio.visit.model.ServiceVisit;
import serwio.visit.repository.VisitTechnicianRepository;

@Component
@RequiredArgsConstructor
class VisitReservationLocks {
    private final LocationRepository locations;
    private final TechnicianRepository technicians;
    private final VisitTechnicianRepository staff;

    LockedReservation lock(ServiceVisit visit) {
        // Callers first lock the order. Keep location and technician locks in this order.
        var locationId = visit.getServiceOrder().getServiceObject().getLocation().getId();
        var location = locations.findForUpdateById(locationId).orElseThrow();
        var ids = staff.findTechnicianIds(visit.getId());
        for (var id : ids) technicians.findForUpdateById(id).orElseThrow();
        return new LockedReservation(location, ids);
    }

    record LockedReservation(Location location, List<UUID> technicianIds) {}
}
