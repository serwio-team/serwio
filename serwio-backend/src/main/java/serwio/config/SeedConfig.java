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
import serwio.company.model.ServiceBranch;
import serwio.company.model.ServiceCompany;
import serwio.company.repository.ServiceBranchRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.location.model.Location;
import serwio.location.repository.LocationRepository;
import serwio.owner.model.AssetOwner;
import serwio.owner.repository.AssetOwnerRepository;
import serwio.technician.model.Technician;
import serwio.technician.model.TechnicianBranchHistory;
import serwio.technician.repository.TechnicianBranchHistoryRepository;
import serwio.technician.repository.TechnicianRepository;

@Slf4j
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.seed.enabled", havingValue = "true")
public class SeedConfig {
    private static final String DEMO_EMAIL = "owner@example.com";
    private static final String DEMO_LOCATION_NAME = "Lokalizacja testowa";
    private static final String DEMO_COMPANY_EMAIL = "company@example.com";
    private static final String DEMO_TECHNICIAN_EMAIL = "technician@example.com";
    private static final String DEMO_BRANCH_NAME = "Oddział główny";

    private final UserAccountRepository accounts;
    private final AssetOwnerRepository owners;
    private final LocationRepository locations;
    private final ServiceObjectRepository objects;
    private final ServiceCompanyRepository companies;
    private final ServiceBranchRepository branches;
    private final TechnicianRepository technicians;
    private final TechnicianBranchHistoryRepository branchHistory;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.demo.seed.password:}")
    private String seedPassword;

    @Bean
    CommandLineRunner seedDemoLocation(PlatformTransactionManager transactionManager) {
        return args -> {
            var transactions = new TransactionTemplate(transactionManager);
            var locationId = transactions.execute(status -> {
                var account = prepareAccount(DEMO_EMAIL, "ASSET_OWNER");
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
            var branchId = transactions.execute(status -> seedCompany());
            log.info("Dane startowe firmy gotowe. Konta {} i {}, oddział testowy — branchId: {}",
                    DEMO_COMPANY_EMAIL, DEMO_TECHNICIAN_EMAIL, branchId);
        };
    }

    private UserAccount prepareAccount(String email, String role) {
        var account = accounts.findByEmail(email).orElseGet(() -> createAccount(email, role));
        if (!role.equals(account.getRole())) {
            throw new IllegalStateException("Konto " + email + " nie ma roli " + role);
        }
        if ("!seed-account-without-login".equals(account.getPasswordHash())) {
            initializeCredentials(account);
        }
        return account;
    }

    private UserAccount createAccount(String email, String role) {
        var account = new UserAccount();
        account.setEmail(email);
        account.setRole(role);
        initializeCredentials(account);
        account.setCreatedAt(now());
        return accounts.save(account);
    }

    private void initializeCredentials(UserAccount account) {
        var password = seedPassword.isBlank() ? UUID.randomUUID().toString() : seedPassword;
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setStatus("ACTIVE");
        if (seedPassword.isBlank()) {
            log.warn("Wygenerowane hasło konta testowego {}: {}", account.getEmail(), password);
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

    private UUID seedCompany() {
        var companyAccount = prepareAccount(DEMO_COMPANY_EMAIL, "SERVICE_COMPANY");
        var company = companies.findByUserId(companyAccount.getId())
                .orElseGet(() -> createCompany(companyAccount));
        var branch = branches.findFirstByServiceCompanyIdAndNameOrderByCreatedAtAscIdAsc(
                        company.getId(), DEMO_BRANCH_NAME)
                .orElseGet(() -> createBranch(company));
        var technicianAccount = prepareAccount(DEMO_TECHNICIAN_EMAIL, "TECHNICIAN");
        if (technicians.findByUserId(technicianAccount.getId()).isEmpty()) {
            createTechnician(technicianAccount, branch, companyAccount);
        }
        return branch.getId();
    }

    private ServiceCompany createCompany(UserAccount account) {
        var company = new ServiceCompany();
        company.setUser(account);
        company.setName("Firma serwisowa testowa");
        company.setCreatedAt(now());
        return companies.save(company);
    }

    private ServiceBranch createBranch(ServiceCompany company) {
        var branch = new ServiceBranch();
        branch.setServiceCompany(company);
        branch.setName(DEMO_BRANCH_NAME);
        branch.setActive(true);
        branch.setCreatedAt(now());
        return branches.save(branch);
    }

    private void createTechnician(UserAccount account, ServiceBranch branch, UserAccount changedBy) {
        var technician = new Technician();
        technician.setUser(account);
        technician.setBranch(branch);
        technician.setDisplayName("Serwisant testowy");
        technician.setActive(true);
        technician.setCreatedAt(now());
        technicians.save(technician);

        var membership = new TechnicianBranchHistory();
        membership.setTechnician(technician);
        membership.setBranch(branch);
        membership.setChangedByUser(changedBy);
        membership.setValidFrom(technician.getCreatedAt());
        branchHistory.save(membership);
    }
}
