package serwio.asset.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
import java.util.Map;
import java.util.UUID;
import serwio.location.model.Location;

@Entity
@Table(name = "service_object")
@Getter
@Setter
@NoArgsConstructor
public class ServiceObject {

    @jakarta.validation.constraints.AssertTrue(message = "Wygenerowany QR wymaga publicznego identyfikatora")
    @jakarta.persistence.Transient
    public boolean isQrIdentifierPresent() {
        return qrGeneratedAt == null || publicId != null;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false, unique = false)
    @NotNull
    private Location location;

    @Column(name = "public_id", nullable = true, unique = true, columnDefinition = "uuid")
    private UUID publicId;

    @Column(name = "name", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 255)
    private String name;

    @Column(name = "description", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String description;

    @Column(name = "type", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 100)
    private String type;

    @Column(name = "manufacturer", nullable = true, unique = false, columnDefinition = "varchar")
    @Size(max = 255)
    private String manufacturer;

    @Column(name = "model", nullable = true, unique = false, columnDefinition = "varchar")
    @Size(max = 255)
    private String model;

    @Column(name = "serial_number", nullable = true, unique = false, columnDefinition = "varchar")
    @Size(max = 255)
    private String serialNumber;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "attributes", nullable = true, unique = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes;

    @Column(name = "qr_generated_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime qrGeneratedAt;

    @Column(name = "active", nullable = false, unique = false, columnDefinition = "boolean")
    @NotNull
    private Boolean active;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;
}
