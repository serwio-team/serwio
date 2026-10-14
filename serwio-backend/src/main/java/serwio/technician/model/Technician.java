package serwio.technician.model;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
import serwio.company.model.ServiceBranch;
import serwio.account.model.UserAccount;

@Entity
@Table(name = "technician")
@Getter
@Setter
@NoArgsConstructor
public class Technician {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @NotNull
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, unique = false)
    @NotNull
    private ServiceBranch branch;

    @Column(name = "display_name", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 255)
    private String displayName;

    @Column(name = "active", nullable = false, unique = false, columnDefinition = "boolean")
    @NotNull
    private Boolean active;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;
}
