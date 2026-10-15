package serwio.location.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
import serwio.owner.model.AssetOwner;

@Entity
@Table(name = "location")
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_owner_id", nullable = false, unique = false)
    @NotNull
    private AssetOwner assetOwner;

    @Column(name = "name", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Column(name = "address_line", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 500)
    private String addressLine;

    @Column(name = "city", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 150)
    private String city;

    @Column(name = "postal_code", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 32)
    private String postalCode;

    @Column(name = "country", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 2)
    @Pattern(regexp = "[A-Z]{2}", message = "Kod kraju musi zawierać dwie wielkie litery, np. PL")
    private String country;

    @Column(name = "timezone", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 100)
    @serwio.validation.ValidTimeZone
    private String timezone;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;
}
