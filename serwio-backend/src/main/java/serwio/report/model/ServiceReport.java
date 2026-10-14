package serwio.report.model;

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

@ValidPeriod(start = "createdAt", end = "updatedAt", allowEqual = true)
@ValidPeriod(start = "createdAt", end = "finalizedAt", allowEqual = true)
@Entity
@Table(name = "service_report")
@Getter
@Setter
@NoArgsConstructor
public class ServiceReport {

    @jakarta.validation.constraints.AssertTrue(message = "Finalny raport wymaga czynności, diagnozy i rozwiązania")
    @jakarta.persistence.Transient
    public boolean isFinalizationContentValid() {
        return finalizedAt == null || (hasText(actionsDone) && hasText(diagnosis) && hasText(resolution));
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_id", nullable = false, unique = true)
    @NotNull
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_technician_id", nullable = false, unique = false)
    @NotNull
    private Technician authorTechnician;

    @Column(name = "actions_done", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String actionsDone;

    @Column(name = "diagnosis", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String diagnosis;

    @Column(name = "resolution", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String resolution;

    @Column(name = "used_materials", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String usedMaterials;

    @Column(name = "recommendations", nullable = true, unique = false, columnDefinition = "text")
    @Size(max = 100000)
    private String recommendations;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime updatedAt;

    @Column(name = "finalized_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime finalizedAt;
}
