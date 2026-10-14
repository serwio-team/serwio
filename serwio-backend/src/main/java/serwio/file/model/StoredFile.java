package serwio.file.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

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
import serwio.account.model.UserAccount;

@Entity
@Table(name = "stored_file")
@Getter
@Setter
@NoArgsConstructor
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false, unique = false)
    @NotNull
    private UserAccount uploadedByUser;

    @Column(name = "storage_key", nullable = false, unique = true, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 1024)
    private String storageKey;

    @Column(name = "original_name", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 127)
    @Pattern(regexp = "[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+", message = "Nieprawidłowy typ MIME")
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, unique = false, columnDefinition = "bigint")
    @NotNull
    @PositiveOrZero
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;
}
