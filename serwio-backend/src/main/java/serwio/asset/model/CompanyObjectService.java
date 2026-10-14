package serwio.asset.model;

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
import serwio.company.model.ServiceCompany;
import serwio.account.model.UserAccount;

@ValidPeriod(start = "validFrom", end = "validTo")
@Entity
@Table(name = "company_object_service")
@Getter
@Setter
@NoArgsConstructor
public class CompanyObjectService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, unique = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_company_id", nullable = false, unique = false)
    @NotNull
    private ServiceCompany serviceCompany;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_object_id", nullable = false, unique = false)
    @NotNull
    private ServiceObject serviceObject;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "authorized_by_user_id", nullable = false, unique = false)
    @NotNull
    private UserAccount authorizedByUser;

    @Column(name = "valid_from", nullable = false, unique = false, columnDefinition = "timestamp with time zone")
    @NotNull
    private OffsetDateTime validFrom;

    @Column(name = "valid_to", nullable = true, unique = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime validTo;
}
