package serwio.technician.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.account.model.UserAccount;
import serwio.account.repository.UserAccountRepository;
import serwio.company.model.ServiceBranch;
import serwio.company.model.ServiceCompany;
import serwio.company.repository.ServiceBranchRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ConflictException;
import serwio.exception.ResourceNotFoundException;
import serwio.security.CurrentUser;
import serwio.technician.dto.TechnicianCreateRequest;
import serwio.technician.dto.TechnicianPasswordRequest;
import serwio.technician.dto.TechnicianResponse;
import serwio.technician.dto.TechnicianUpdateRequest;
import serwio.technician.model.Technician;
import serwio.technician.model.TechnicianBranchHistory;
import serwio.technician.repository.TechnicianBranchHistoryRepository;
import serwio.technician.repository.TechnicianRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TechnicianService {
    private final TechnicianRepository technicians;
    private final TechnicianBranchHistoryRepository branchHistory;
    private final ServiceBranchRepository branches;
    private final ServiceCompanyRepository companies;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public List<TechnicianResponse> list(UUID branchId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("displayName"), Sort.Order.asc("id")));
        var userId = currentUser.id();
        var result = branchId == null
                ? technicians.findAllByBranchServiceCompanyUserId(userId, pageable)
                : technicians.findAllByBranchIdAndBranchServiceCompanyUserId(branchId, userId, pageable);
        return result.getContent().stream().map(this::toResponse).toList();
    }

    public TechnicianResponse get(UUID id) {
        return toResponse(technicians.findByIdAndBranchServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id)));
    }

    @Transactional
    public TechnicianResponse create(TechnicianCreateRequest request) {
        var company = lockCompany();
        var branch = lockActiveBranch(request.branchId());
        var email = request.email().strip().toLowerCase(Locale.ROOT);
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Konto z adresem " + email + " już istnieje.");
        }
        var now = now();
        var account = new UserAccount();
        account.setEmail(email);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setRole("TECHNICIAN");
        account.setStatus("ACTIVE");
        account.setCreatedAt(now);
        accounts.saveAndFlush(account);

        var technician = new Technician();
        technician.setUser(account);
        technician.setBranch(branch);
        technician.setDisplayName(request.displayName().strip());
        technician.setActive(true);
        technician.setCreatedAt(now);
        technicians.save(technician);
        openMembership(technician, branch, company.getUser(), now);
        return toResponse(technician);
    }

    @Transactional
    public TechnicianResponse update(UUID id, TechnicianUpdateRequest request) {
        var company = lockCompany();
        var technician = technicians.findForUpdateByIdAndBranchServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        technician.setDisplayName(request.displayName().strip());

        if (!technician.getBranch().getId().equals(request.branchId())) {
            var branch = lockActiveBranch(request.branchId());
            var now = now();
            branchHistory.findAllByTechnicianIdAndValidToIsNull(id)
                    .forEach(membership -> membership.setValidTo(now));
            technician.setBranch(branch);
            openMembership(technician, branch, company.getUser(), now);
        }

        if (request.active() && !technician.getBranch().getActive()) {
            throw new ConflictException("Aktywny serwisant musi należeć do aktywnego oddziału.");
        }
        technician.setActive(request.active());
        technician.getUser().setStatus(request.active() ? "ACTIVE" : "INACTIVE");
        return toResponse(technician);
    }

    @Transactional
    public void changePassword(UUID id, TechnicianPasswordRequest request) {
        var technician = technicians.findForUpdateByIdAndBranchServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        technician.getUser().setPasswordHash(passwordEncoder.encode(request.password()));
    }

    private ServiceCompany lockCompany() {
        return companies.findForUpdateByUserId(currentUser.id()).orElseThrow(() ->
                new AccessDeniedException("Konto nie ma profilu firmy serwisowej"));
    }

    private ServiceBranch lockActiveBranch(UUID branchId) {
        var branch = branches.findForUpdateByIdAndServiceCompanyUserId(branchId, currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono oddziału: " + branchId));
        if (!branch.getActive()) {
            throw new ConflictException("Nie można przypisać serwisanta do nieaktywnego oddziału.");
        }
        return branch;
    }

    private void openMembership(Technician technician, ServiceBranch branch, UserAccount changedBy,
                                OffsetDateTime validFrom) {
        var membership = new TechnicianBranchHistory();
        membership.setTechnician(technician);
        membership.setBranch(branch);
        membership.setChangedByUser(changedBy);
        membership.setValidFrom(validFrom);
        branchHistory.save(membership);
    }

    private TechnicianResponse toResponse(Technician technician) {
        var branch = technician.getBranch();
        var user = technician.getUser();
        return new TechnicianResponse(technician.getId(), user.getId(), user.getEmail(),
                technician.getDisplayName(), branch.getId(), branch.getName(), technician.getActive(),
                technician.getCreatedAt());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono serwisanta: " + id);
    }
}
