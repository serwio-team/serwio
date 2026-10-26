package serwio.account.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_account")
@Getter
@Setter
@NoArgsConstructor
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 254)
    @Email
    private String email;

    @Column(name = "password_hash", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String role;

    @Column(name = "status", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String status;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;

    @Column(name = "password_change_required", nullable = false, unique = false,
            columnDefinition = "boolean default false")
    @NotNull
    private Boolean passwordChangeRequired = false;
}
