package serwio.visit.model;

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
import serwio.validation.ValidPeriod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
import serwio.account.model.UserAccount;

@ValidPeriod(start = "proposedStartsAt", end = "proposedEndsAt", allowOpenEnd = false)
@ValidPeriod(start = "createdAt", end = "respondedAt", allowEqual = true)
@Entity
@Table(name = "visit_proposal")
@Getter
@Setter
@NoArgsConstructor
public class VisitProposal {

    @jakarta.validation.constraints.AssertTrue(message = "Odpowiedź wymaga jednocześnie autora i daty")
    @jakarta.persistence.Transient
    public boolean isResponseComplete() {
        return (respondedByUser == null) == (respondedAt == null);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, unique = false)
    @NotNull
    private ServiceVisit visit;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_by_user_id", nullable = false, unique = false)
    @NotNull
    private UserAccount proposedByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "responded_by_user_id", nullable = true, unique = false)
    private UserAccount respondedByUser;

    @Column(name = "proposed_starts_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime proposedStartsAt;

    @Column(name = "proposed_ends_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime proposedEndsAt;

    @Column(name = "status", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String status;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;

    @Column(name = "responded_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime respondedAt;
}
