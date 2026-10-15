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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import serwio.validation.ValidPeriod;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.OffsetDateTime;
import java.util.UUID;
import serwio.order.model.ServiceOrder;
import serwio.technician.model.Technician;

@ValidPeriod(start = "startsAt", end = "endsAt", allowBothNull = true, allowOpenEnd = false)
@Entity
@Table(name = "service_visit")
@Getter
@Setter
@NoArgsConstructor
public class ServiceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_id", nullable = false, unique = false)
    @NotNull
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coordinator_technician_id", nullable = false, unique = false)
    @NotNull
    private Technician coordinatorTechnician;

    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "accepted_proposal_id", nullable = true, unique = true)
    private VisitProposal acceptedProposal;

    @Column(name = "starts_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime endsAt;

    @Column(name = "status", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String status;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;
}
