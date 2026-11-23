package serwio.validation;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import serwio.account.model.UserAccount;
import serwio.asset.model.ServiceObject;
import serwio.location.model.LocationAvailability;
import serwio.report.model.ServiceReport;
import serwio.visit.model.VisitProposal;

import static org.assertj.core.api.Assertions.assertThat;

class DomainInvariantsTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-23T11:00:00Z");

    @Test
    void finalizedReportRequiresAllThreeTextFields() {
        var report = new ServiceReport();
        assertThat(report.isFinalizationContentValid()).isTrue();
        report.setFinalizedAt(NOW);
        report.setActionsDone("Czyszczenie");
        report.setDiagnosis("Brudny filtr");
        report.setResolution("  ");
        assertThat(report.isFinalizationContentValid()).isFalse();
        report.setResolution("Wyczyszczono filtr");
        assertThat(report.isFinalizationContentValid()).isTrue();
    }

    @Test
    void proposalResponseRequiresBothActorAndTimestamp() {
        var proposal = new VisitProposal();
        assertThat(proposal.isResponseComplete()).isTrue();
        proposal.setRespondedAt(NOW);
        assertThat(proposal.isResponseComplete()).isFalse();
        proposal.setRespondedByUser(new UserAccount());
        assertThat(proposal.isResponseComplete()).isTrue();
        proposal.setRespondedAt(null);
        assertThat(proposal.isResponseComplete()).isFalse();
    }

    @Test
    void qrTimestampRequiresAPublicIdentifier() {
        var object = new ServiceObject();
        assertThat(object.isQrIdentifierPresent()).isTrue();
        object.setQrGeneratedAt(NOW);
        assertThat(object.isQrIdentifierPresent()).isFalse();
        object.setPublicId(UUID.randomUUID());
        assertThat(object.isQrIdentifierPresent()).isTrue();
    }

    @Test
    void normalizedAvailabilityRequiresADistinctEndOrTheMidnightMarker() {
        var availability = new LocationAvailability();
        assertThat(availability.isIntervalValid()).isTrue();
        availability.setAvailableFrom(LocalTime.of(22, 0));
        assertThat(availability.isIntervalValid()).isTrue();
        availability.setAvailableTo(LocalTime.of(22, 0));
        assertThat(availability.isIntervalValid()).isFalse();
        availability.setAvailableTo(LocalTime.of(23, 0));
        assertThat(availability.isIntervalValid()).isTrue();
        availability.setEndsAtMidnight(true);
        assertThat(availability.isIntervalValid()).isFalse();
        availability.setAvailableTo(LocalTime.MIDNIGHT);
        assertThat(availability.isIntervalValid()).isTrue();
    }
}
