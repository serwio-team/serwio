package serwio.validation;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PeriodValidatorTest {
    @Test
    void validatesLocalPeriodsAndReportsTheInvalidEndpoint() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var morning = LocalTime.of(9, 0);
            var noon = LocalTime.NOON;
            assertThat(validator.validate(new LocalPeriod(morning, noon))).isEmpty();
            assertThat(validator.validate(new LocalPeriod(null, null))).isEmpty();
            assertThat(validator.validate(new LocalPeriod(null, noon)))
                    .singleElement().satisfies(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("start"));
            for (var end : new LocalTime[] {null, morning, morning.minusMinutes(1)}) {
                assertThat(validator.validate(new LocalPeriod(morning, end)))
                        .singleElement().satisfies(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("end"));
            }
        }
    }

    @Test
    void comparesOffsetTimesByInstantAndAllowsAnOpenEndOnlyWhenConfigured() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var start = OffsetDateTime.parse("2026-11-23T11:00:00+01:00");
            var equalInUtc = OffsetDateTime.parse("2026-11-23T10:00:00Z");
            assertThat(validator.validate(new OffsetPeriod(start, equalInUtc))).isEmpty();
            assertThat(validator.validate(new OffsetPeriod(start, null))).isEmpty();
            assertThat(validator.validate(new OffsetPeriod(start, equalInUtc.minusMinutes(1)))).hasSize(1);
            assertThat(validator.validate(new OffsetPeriod(null, null))).hasSize(1);
            assertThatThrownBy(() -> validator.validate(new MixedPeriod(start, LocalTime.NOON)))
                    .isInstanceOf(ValidationException.class).hasRootCauseInstanceOf(ValidationException.class);
        }
    }

    @ValidPeriod(start = "start", end = "end", allowBothNull = true, allowOpenEnd = false)
    public record LocalPeriod(LocalTime start, LocalTime end) {}

    @ValidPeriod(start = "start", end = "end", allowEqual = true)
    public record OffsetPeriod(OffsetDateTime start, OffsetDateTime end) {}

    @ValidPeriod(start = "start", end = "end")
    public record MixedPeriod(OffsetDateTime start, LocalTime end) {}
}
