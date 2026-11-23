package serwio.visit.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.util.List;
import java.util.Map;
import serwio.exception.FieldValidationException;
import serwio.location.model.LocationAvailability;
import serwio.visit.dto.VisitTimeRange;

final class VisitCalendar {
    private VisitCalendar() {}

    static void validateRequest(VisitTimeRange range, ZoneId zone, Instant now) {
        validateOffset("startsAt", range.startsAt(), zone);
        validateOffset("endsAt", range.endsAt(), zone);
        if (!range.startsAt().toInstant().isAfter(now)) {
            throw invalid("startsAt", "Początek wizyty musi być w przyszłości.");
        }
        if (!range.endsAt().isAfter(range.startsAt())) {
            throw invalid("endsAt", "Koniec wizyty musi być późniejszy niż początek.");
        }
    }

    static void requireAvailable(VisitTimeRange range, ZoneId zone, List<LocationAvailability> windows, Instant now) {
        var start = range.startsAt().toInstant();
        var end = range.endsAt().toInstant();
        var date = start.atZone(zone).toLocalDate();
        if (start.isAfter(now) && end.isAfter(start)
                && windows.stream().filter(window -> window.getDayOfWeek() == date.getDayOfWeek().getValue())
                .anyMatch(window -> covers(start, end, zone.getRules(), date.atTime(window.getAvailableFrom()),
                        window.isEndsAtMidnight() ? date.plusDays(1).atStartOfDay() : date.atTime(window.getAvailableTo())))) {
            return;
        }
        throw new VisitConflictException("OUTSIDE_LOCATION_AVAILABILITY", "Termin jest poza dostępnym oknem lokalizacji.");
    }

    private static boolean covers(Instant start, Instant end, ZoneRules rules,
                                  LocalDateTime windowStart, LocalDateTime windowEnd) {
        var segmentStart = start;
        while (segmentStart.isBefore(end)) {
            var offset = rules.getOffset(segmentStart);
            var transition = rules.nextTransition(segmentStart);
            var segmentEnd = transition != null && transition.getInstant().isBefore(end) ? transition.getInstant() : end;
            // Oceniaj obie granice każdej części w tym samym przesunięciu UTC.
            if (LocalDateTime.ofInstant(segmentStart, offset).isBefore(windowStart)
                    || LocalDateTime.ofInstant(segmentEnd, offset).isAfter(windowEnd)) {
                return false;
            }
            segmentStart = segmentEnd;
        }
        return true;
    }

    private static void validateOffset(String field, OffsetDateTime time, ZoneId zone) {
        if (!zone.getRules().isValidOffset(time.toLocalDateTime(), time.getOffset())) {
            throw invalid(field, "Data i przesunięcie UTC muszą być zgodne ze strefą lokalizacji.");
        }
    }

    private static FieldValidationException invalid(String field, String message) {
        return new FieldValidationException(Map.of(field, List.of(message)));
    }
}
