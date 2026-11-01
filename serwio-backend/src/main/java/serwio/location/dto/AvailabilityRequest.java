package serwio.location.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AvailabilityRequest(
        @NotNull @Size(max = 7) List<@NotNull @Valid Day> days) {
    public record Day(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull @Size(max = 100) List<@NotNull @Valid Interval> intervals) {}

    public record Interval(
            @NotNull @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]", message = "Podaj godzinę HH:mm") String from,
            @NotNull @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]|24:00", message = "Podaj godzinę HH:mm lub 24:00") String to) {}
}
