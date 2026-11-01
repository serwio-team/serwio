package serwio.location.service;

import java.time.LocalTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import serwio.exception.ResourceNotFoundException;
import serwio.location.dto.AvailabilityRequest;
import serwio.location.dto.AvailabilityResponse;
import serwio.location.model.Location;
import serwio.location.model.LocationAvailability;
import serwio.location.repository.LocationAvailabilityRepository;
import serwio.location.repository.LocationRepository;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationAvailabilityService {
    private final LocationRepository locations;
    private final LocationAvailabilityRepository availability;
    private final CurrentUser currentUser;

    public AvailabilityResponse get(UUID id) {
        var location = locations.findByIdAndAssetOwnerUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        return response(location, availability.findAllByLocationIdOrderByDayOfWeekAscAvailableFromAsc(id));
    }

    @Transactional
    public AvailabilityResponse replace(UUID id, AvailabilityRequest request) {
        var location = locations.findForUpdateByIdAndAssetOwnerUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        List<LocationAvailability> rows = new ArrayList<>();
        Set<Integer> days = new HashSet<>();
        for (var day : request.days()) {
            if (!days.add(day.dayOfWeek())) throw invalid("Dzień tygodnia nie może się powtarzać.");
            for (var interval : day.intervals()) {
                var from = LocalTime.parse(interval.from());
                var to = "24:00".equals(interval.to()) ? LocalTime.MIDNIGHT : LocalTime.parse(interval.to());
                if (from.equals(to) && !"24:00".equals(interval.to()))
                    throw invalid("Początek i koniec przedziału nie mogą być równe.");
                if ("24:00".equals(interval.to()) || !to.isAfter(from)) {
                    rows.add(row(location, day.dayOfWeek(), from, LocalTime.MIDNIGHT, true));
                    if (!to.equals(LocalTime.MIDNIGHT))
                        rows.add(row(location, day.dayOfWeek() % 7 + 1, LocalTime.MIDNIGHT, to, false));
                } else rows.add(row(location, day.dayOfWeek(), from, to, false));
            }
        }
        rows.sort(Comparator.comparing(LocationAvailability::getDayOfWeek)
                .thenComparing(LocationAvailability::getAvailableFrom));
        LocationAvailability previous = null;
        for (var row : rows) {
            if (previous != null && previous.getDayOfWeek().equals(row.getDayOfWeek())
                    && (previous.isEndsAtMidnight() || previous.getAvailableTo().isAfter(row.getAvailableFrom())))
                throw invalid("Przedziały dostępności nie mogą się nakładać.");
            previous = row;
        }
        availability.deleteAll(availability.findAllByLocationIdOrderByDayOfWeekAscAvailableFromAsc(id));
        availability.flush();
        availability.saveAll(rows);
        return response(location, rows);
    }

    private LocationAvailability row(Location location, int day, LocalTime from, LocalTime to, boolean midnight) {
        var row = new LocationAvailability();
        row.setLocation(location);
        row.setDayOfWeek((short) day);
        row.setAvailableFrom(from);
        row.setAvailableTo(to);
        row.setEndsAtMidnight(midnight);
        return row;
    }

    private AvailabilityResponse response(Location location, List<LocationAvailability> rows) {
        Map<Integer, List<AvailabilityRequest.Interval>> days = new TreeMap<>();
        Set<LocationAvailability> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var row : rows) {
            if (!row.isEndsAtMidnight() || row.getAvailableFrom().equals(LocalTime.MIDNIGHT)) continue;
            var next = rows.stream().filter(candidate -> candidate.getDayOfWeek() == row.getDayOfWeek() % 7 + 1
                    && candidate.getAvailableFrom().equals(LocalTime.MIDNIGHT) && !candidate.isEndsAtMidnight())
                    .findFirst();
            if (next.isPresent()) {
                consumed.add(row);
                consumed.add(next.get());
                add(days, row, next.get().getAvailableTo().toString());
            }
        }
        for (var row : rows) {
            if (!consumed.contains(row)) add(days, row, row.isEndsAtMidnight() ? "24:00" : row.getAvailableTo().toString());
        }
        var result = days.entrySet().stream().map(entry -> new AvailabilityRequest.Day(entry.getKey(),
                entry.getValue().stream().sorted(Comparator.comparing(AvailabilityRequest.Interval::from)).toList())).toList();
        return new AvailabilityResponse(location.getId(), location.getTimezone(), result);
    }

    private void add(Map<Integer, List<AvailabilityRequest.Interval>> days, LocationAvailability row, String to) {
        days.computeIfAbsent((int) row.getDayOfWeek(), key -> new ArrayList<>())
                .add(new AvailabilityRequest.Interval(row.getAvailableFrom().toString(), to));
    }

    private ResponseStatusException invalid(String detail) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono lokalizacji: " + id);
    }
}
