package serwio.location.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import serwio.validation.ValidPeriod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalTime;
import java.util.UUID;

@ValidPeriod(start = "availableFrom", end = "availableTo", allowOpenEnd = false)
@Entity
@Table(name = "location_availability")
@Getter
@Setter
@NoArgsConstructor
public class LocationAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, unique = false)
    @NotNull
    private Location location;

    @Column(name = "day_of_week", nullable = false, unique = false, columnDefinition = "smallint")
    @NotNull
    @Min(1)
    @Max(7)
    private Short dayOfWeek;

    @Column(name = "available_from", nullable = false, unique = false, columnDefinition = "time")
    @NotNull
    private LocalTime availableFrom;

    @Column(name = "available_to", nullable = false, unique = false, columnDefinition = "time")
    @NotNull
    private LocalTime availableTo;
}
