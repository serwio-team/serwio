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
import java.util.UUID;
import serwio.file.model.StoredFile;

@Entity
@Table(name = "service_object_file")
@Getter
@Setter
@NoArgsConstructor
public class ServiceObjectFile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_object_id", nullable = false, unique = false)
    @NotNull
    private ServiceObject serviceObject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, unique = false)
    @NotNull
    private StoredFile file;

    @Column(name = "type", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 100)
    private String type;
}
