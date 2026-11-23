package serwio.report.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import serwio.account.model.UserAccount;
import serwio.technician.model.Technician;

@Entity
@Table(name = "report_author_history")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportAuthorHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_report_id", nullable = false)
    private ServiceReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_author_technician_id")
    private Technician previousAuthor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "new_author_technician_id", nullable = false)
    private Technician newAuthor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private UserAccount changedBy;

    @Column(name = "changed_at", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime changedAt;

    public ReportAuthorHistory(ServiceReport report, Technician previousAuthor, Technician newAuthor,
                               UserAccount changedBy, OffsetDateTime changedAt) {
        this.report = report;
        this.previousAuthor = previousAuthor;
        this.newAuthor = newAuthor;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }
}
