package serwio.order.model;

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
import serwio.company.model.ServiceCompany;
import serwio.asset.model.ServiceObject;
import serwio.technician.model.Technician;
import serwio.account.model.UserAccount;

@ValidPeriod(start = "createdAt", end = "closedAt", allowEqual = true)
@Entity
@Table(name = "service_order")
@Getter
@Setter
@NoArgsConstructor
public class ServiceOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_object_id", nullable = false, unique = false)
    @NotNull
    private ServiceObject serviceObject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_company_id", nullable = false, unique = false)
    @NotNull
    private ServiceCompany serviceCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false, unique = false)
    @NotNull
    private UserAccount createdByUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "coordinator_technician_id", nullable = true, unique = false)
    private Technician coordinatorTechnician;

    @Column(name = "order_type", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String orderType;

    @Column(name = "description", nullable = false, unique = false, columnDefinition = "text")
    @NotBlank
    @Size(max = 100000)
    private String description;

    @Column(name = "current_status", nullable = false, unique = false, columnDefinition = "varchar")
    @NotBlank
    @Size(max = 64)
    private String currentStatus;

    @Column(name = "created_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime createdAt;

    @Column(name = "closed_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime closedAt;
}
