package serwio.visit.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import serwio.exception.FieldValidationException;
import serwio.location.model.LocationAvailability;
import serwio.visit.dto.VisitTimeRange;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisitCalendarTest {
    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Test
    void requiresOneWholeWindowIncludingItsBoundary() {
        var range = range("2026-12-01T08:00+01:00", "2026-12-01T16:00+01:00");
        available(range, window(2, "08:00", "16:00"));
        unavailable(range);
        unavailable(range, window(2, "08:00", "12:00"), window(2, "12:00", "16:00"));
        unavailable(range, window(2, "08:00", "12:00"), window(2, "13:00", "16:00"));
    }

    @Test
    void canEndAtMidnightButCannotCrossIntoAnotherWindow() {
        var late = window(2, "22:00", "24:00");
        available(range("2026-12-01T23:00+01:00", "2026-12-02T00:00+01:00"), late);
        unavailable(range("2026-12-01T23:00+01:00", "2026-12-02T01:00+01:00"), late, window(3, "00:00", "02:00"));
    }

    @Test
    void skipsNonexistentClockTimesWhenCheckingSpringTransition() {
        var range = range("2027-03-28T01:30+01:00", "2027-03-28T03:15+02:00");
        VisitCalendar.validateRequest(range, WARSAW, NOW);
        available(range, window(7, "01:00", "04:00"));
        assertThatThrownBy(() -> VisitCalendar.validateRequest(
                range("2027-03-28T02:30+01:00", "2027-03-28T04:00+02:00"), WARSAW, NOW))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void checksBothPassesThroughTheRepeatedHour() {
        var range = range("2027-10-31T02:30+02:00", "2027-10-31T02:30+01:00");
        VisitCalendar.validateRequest(range, WARSAW, NOW);
        available(range, window(7, "02:00", "03:00"));
        unavailable(range, window(7, "02:15", "02:45"));
    }

    @Test
    void rejectsNonpositiveDurationAndStartThatIsNoLongerInTheFuture() {
        assertThatThrownBy(() -> VisitCalendar.validateRequest(
                range("2026-12-01T10:00+01:00", "2026-12-01T10:00+01:00"), WARSAW, NOW))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> VisitCalendar.validateRequest(
                range("2026-11-23T13:00+01:00", "2026-11-23T14:00+01:00"), WARSAW, NOW))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void availabilityRechecksTimeAtAcceptanceAndSupportsAZoneWithoutClockChanges() {
        var utc = ZoneId.of("UTC");
        var future = range("2026-12-01T09:00Z", "2026-12-01T10:00Z");
        var officeHours = List.of(window(2, "08:00", "16:00"));
        VisitCalendar.requireAvailable(future, utc, officeHours, NOW);
        assertThatThrownBy(() -> VisitCalendar.requireAvailable(future, utc, officeHours, future.startsAt().toInstant()))
                .isInstanceOf(VisitConflictException.class);
        assertThatThrownBy(() -> VisitCalendar.requireAvailable(new VisitTimeRange(future.startsAt(), future.startsAt()),
                utc, officeHours, NOW)).isInstanceOf(VisitConflictException.class);
    }

    private static void available(VisitTimeRange range, LocationAvailability... windows) {
        VisitCalendar.requireAvailable(range, WARSAW, List.of(windows), NOW);
    }

    private static void unavailable(VisitTimeRange range, LocationAvailability... windows) {
        assertThatThrownBy(() -> available(range, windows)).isInstanceOf(VisitConflictException.class);
    }

    private static VisitTimeRange range(String startsAt, String endsAt) {
        return new VisitTimeRange(OffsetDateTime.parse(startsAt), OffsetDateTime.parse(endsAt));
    }

    private static LocationAvailability window(int day, String start, String end) {
        var window = new LocationAvailability();
        window.setDayOfWeek((short) day);
        window.setAvailableFrom(LocalTime.parse(start));
        window.setEndsAtMidnight(end.equals("24:00"));
        window.setAvailableTo(window.isEndsAtMidnight() ? LocalTime.MIDNIGHT : LocalTime.parse(end));
        return window;
    }
}
