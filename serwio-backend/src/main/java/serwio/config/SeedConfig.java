package serwio.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.seed.enabled", havingValue = "true")
public class SeedConfig {
    private static final String DEMO_EMAIL = "demo-owner@serwio.local";
    private static final String DEMO_LOCATION_NAME = "Lokalizacja testowa";

    private final UserAccountRepository accounts;
    private final AssetOwnerRepository owners;
    private final LocationRepository locations;

    @Bean
    CommandLineRunner seedDemoLocation(PlatformTransactionManager transactionManager) {
        return args -> {
            var locationId = new TransactionTemplate(transactionManager).execute(status -> {
                var account = accounts.findByEmail(DEMO_EMAIL).orElseGet(this::createAccount);
                if (!"ASSET_OWNER".equals(account.getRole())) {
                    throw new IllegalStateException("Konto " + DEMO_EMAIL + " nie ma roli ASSET_OWNER");
                }
                var owner = owners.findByUserId(account.getId()).orElseGet(() -> createOwner(account));
                var location = locations.findFirstByAssetOwnerIdAndNameOrderByCreatedAtAscIdAsc(
                                owner.getId(), DEMO_LOCATION_NAME)
                        .orElseGet(() -> createLocation(owner));
                return location.getId();
            });
            log.info("Dane startowe gotowe. Lokalizacja testowa — locationId: {}", locationId);
        };
    }

    private UserAccount createAccount() {
        var account = new UserAccount();
        account.setEmail(DEMO_EMAIL);
        account.setPasswordHash("!seed-account-without-login");
        account.setRole("ASSET_OWNER");
        account.setStatus("INACTIVE");
        account.setCreatedAt(now());
        return accounts.save(account);
    }

    private AssetOwner createOwner(UserAccount account) {
        var owner = new AssetOwner();
        owner.setUser(account);
        owner.setName("Właściciel testowy");
        owner.setCreatedAt(now());
        return owners.save(owner);
    }

    private Location createLocation(AssetOwner owner) {
        var location = new Location();
        location.setAssetOwner(owner);
        location.setName(DEMO_LOCATION_NAME);
        location.setAddressLine("Testowa 1");
        location.setCity("Warszawa");
        location.setPostalCode("00-001");
        location.setCountry("PL");
        location.setTimezone("Europe/Warsaw");
        location.setCreatedAt(now());
        return locations.save(location);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
