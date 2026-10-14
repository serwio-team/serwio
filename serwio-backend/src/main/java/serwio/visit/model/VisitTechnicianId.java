package serwio.visit.model;

import jakarta.validation.constraints.NotNull;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class VisitTechnicianId implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column(name = "visit_id", nullable = false)
    @NotNull
    private UUID visitId;

    @Column(name = "technician_id", nullable = false)
    @NotNull
    private UUID technicianId;
}