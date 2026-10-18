package serwio.config;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.asset.model.ServiceObject;
import serwio.asset.repository.ServiceObjectRepository;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.seed.enabled", havingValue = "true")
public class SeedConfig {
    private static final String DEMO_EMAIL = "owner@example.com";
    private static final String DEMO_LOCATION_NAME = "Lokalizacja testowa";

    private final UserAccountRepository accounts;
    private final AssetOwnerRepository owners;
    private final LocationRepository locations;
    private final ServiceObjectRepository objects;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.seed.password:}")
    private String seedPassword;

    @Bean
    CommandLineRunner seedDemoLocation(PlatformTransactionManager transactionManager) {
        return args -> {
            var locationId = new TransactionTemplate(transactionManager).execute(status -> {
                var account = accounts.findByEmail(DEMO_EMAIL).orElseGet(this::createAccount);
                if (!"ASSET_OWNER".equals(account.getRole())) {
                    throw new IllegalStateException("Konto " + DEMO_EMAIL + " nie ma roli ASSET_OWNER");
                }
                if ("!seed-account-without-login".equals(account.getPasswordHash())) {
                    initializeCredentials(account);
                }
                var owner = owners.findByUserId(account.getId()).orElseGet(() -> createOwner(account));
                var location = locations.findFirstByAssetOwnerIdAndNameOrderByCreatedAtAscIdAsc(
                                owner.getId(), DEMO_LOCATION_NAME)
                        .orElseGet(() -> createLocation(owner));
                seedDevice(location, "Klimatyzator — recepcja", "AC", "Daikin", "Sensira", "DEMO-AC-001");
                seedDevice(location, "Pompa ciepła", "HEAT_PUMP", "Panasonic", "Aquarea", "DEMO-HP-001");
                seedDevice(location, "Centrala wentylacyjna", "VENTILATION", "Vents", "VUT", "DEMO-VT-001");
                return location.getId();
            });
            log.info("Dane startowe gotowe. Lokalizacja testowa — locationId: {}", locationId);
        };
    }

    private UserAccount createAccount() {
        var account = new UserAccount();
        account.setEmail(DEMO_EMAIL);
        account.setRole("ASSET_OWNER");
        initializeCredentials(account);
        account.setCreatedAt(now());
        return accounts.save(account);
    }

    private void initializeCredentials(UserAccount account) {
        var password = seedPassword.isBlank() ? UUID.randomUUID().toString() : seedPassword;
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus("ACTIVE");
        if (seedPassword.isBlank()) {
            log.warn("Wygenerowane hasło konta testowego {}: {}", DEMO_EMAIL, password);
        }
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

    private void seedDevice(Location location, String name, String type,
                            String manufacturer, String model, String serialNumber) {
        if (objects.existsByLocationIdAndSerialNumber(location.getId(), serialNumber)) {
            return;
        }
        var object = new ServiceObject();
        object.setLocation(location);
        object.setName(name);
        object.setType(type);
        object.setManufacturer(manufacturer);
        object.setModel(model);
        object.setSerialNumber(serialNumber);
        object.setActive(true);
        object.setCreatedAt(now());
        objects.save(object);
    }
}
