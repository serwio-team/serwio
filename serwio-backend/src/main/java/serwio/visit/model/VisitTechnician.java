package serwio.visit.model;

import jakarta.validation.constraints.NotNull;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import serwio.technician.model.Technician;

@Entity
@Table(name = "visit_technician")
@Getter
@Setter
@NoArgsConstructor
public class VisitTechnician {

    @EmbeddedId
    @NotNull
    private VisitTechnicianId id = new VisitTechnicianId();

    @MapsId("visitId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "visit_id", nullable = false, unique = false)
    @NotNull
    private ServiceVisit visit;

    @MapsId("technicianId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "technician_id", nullable = false, unique = false)
    @NotNull
    private Technician technician;
}
