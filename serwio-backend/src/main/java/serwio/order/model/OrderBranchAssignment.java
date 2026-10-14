package serwio.order.model;

import jakarta.validation.constraints.NotNull;

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
import serwio.company.model.ServiceBranch;
import serwio.account.model.UserAccount;

@ValidPeriod(start = "assignedAt", end = "unassignedAt", allowEqual = true)
@Entity
@Table(name = "order_branch_assignment")
@Getter
@Setter
@NoArgsConstructor
public class OrderBranchAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_order_id", nullable = false, unique = false)
    @NotNull
    private ServiceOrder serviceOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, unique = false)
    @NotNull
    private ServiceBranch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_user_id", nullable = false, unique = false)
    @NotNull
    private UserAccount assignedByUser;

    @Column(name = "assigned_at", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime assignedAt;

    @Column(name = "unassigned_at", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime unassignedAt;
}
