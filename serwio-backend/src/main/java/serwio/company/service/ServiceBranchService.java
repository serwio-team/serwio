package serwio.company.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.company.dto.ServiceBranchRequest;
import serwio.company.dto.ServiceBranchResponse;
import serwio.company.model.ServiceBranch;
import serwio.company.model.ServiceCompany;
import serwio.company.repository.ServiceBranchRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ConflictException;
import serwio.exception.ResourceNotFoundException;
import serwio.security.CurrentUser;
import serwio.technician.repository.TechnicianRepository;
import serwio.technician.repository.TechnicianRepository.BranchTechnicianCount;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceBranchService {
    private final ServiceBranchRepository branches;
    private final ServiceCompanyRepository companies;
    private final TechnicianRepository technicians;
    private final CurrentUser currentUser;

    public List<ServiceBranchResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        var result = branches.findAllByServiceCompanyUserId(currentUser.id(), pageable).getContent();
        if (result.isEmpty()) {
            return List.of();
        }
        var counts = technicians.countActiveByBranchIds(result.stream().map(ServiceBranch::getId).toList())
                .stream()
                .collect(Collectors.toMap(BranchTechnicianCount::getBranchId,
                        BranchTechnicianCount::getTechnicianCount));
        return result.stream()
                .map(branch -> toResponse(branch, counts.getOrDefault(branch.getId(), 0L)))
                .toList();
    }

    public ServiceBranchResponse get(UUID id) {
        var branch = branches.findByIdAndServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        return toResponse(branch);
    }

    @Transactional
    public ServiceBranchResponse create(ServiceBranchRequest request) {
        var company = lockCompany();
        var name = request.name().strip();
        if (branches.existsByServiceCompanyIdAndNameIgnoreCase(company.getId(), name)) {
            throw duplicateName(name);
        }
        var branch = new ServiceBranch();
        branch.setServiceCompany(company);
        branch.setName(name);
        branch.setActive(true);
        branch.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return toResponse(branches.save(branch), 0);
    }

    @Transactional
    public ServiceBranchResponse update(UUID id, ServiceBranchRequest request) {
        var company = lockCompany();
        var branch = branches.findForUpdateByIdAndServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        var name = request.name().strip();
        if (branches.existsByServiceCompanyIdAndNameIgnoreCaseAndIdNot(company.getId(), name, id)) {
            throw duplicateName(name);
        }
        branch.setName(name);
        return toResponse(branch);
    }

    @Transactional
    public void deactivate(UUID id) {
        var company = lockCompany();
        var branch = branches.findForUpdateByIdAndServiceCompanyUserId(id, currentUser.id())
                .orElseThrow(() -> notFound(id));
        if (!branch.getActive()) {
            return;
        }
        if (technicians.countByBranchIdAndActiveTrue(id) > 0) {
            throw new ConflictException(
                    "Oddział ma aktywnych serwisantów. Przenieś ich do innego oddziału przed dezaktywacją.");
        }
        if (branches.countByServiceCompanyIdAndActiveTrue(company.getId()) <= 1) {
            throw new ConflictException("Firma serwisowa musi mieć co najmniej jeden aktywny oddział.");
        }
        branch.setActive(false);
    }

    private ServiceCompany lockCompany() {
        return companies.findForUpdateByUserId(currentUser.id()).orElseThrow(() ->
                new AccessDeniedException("Konto nie ma profilu firmy serwisowej"));
    }

    private ServiceBranchResponse toResponse(ServiceBranch branch) {
        return toResponse(branch, technicians.countByBranchIdAndActiveTrue(branch.getId()));
    }

    private ServiceBranchResponse toResponse(ServiceBranch branch, long activeTechnicianCount) {
        return new ServiceBranchResponse(branch.getId(), branch.getName(), branch.getActive(),
                activeTechnicianCount, branch.getCreatedAt());
    }

    private ConflictException duplicateName(String name) {
        return new ConflictException("Oddział o nazwie „" + name + "” już istnieje w tej firmie.");
    }

    private ResourceNotFoundException notFound(UUID id) {
        return new ResourceNotFoundException("Nie znaleziono oddziału: " + id);
    }
}
