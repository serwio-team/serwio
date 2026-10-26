package serwio.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.company.dto.CompanySummaryResponse;
import serwio.company.dto.ServiceCompanyResponse;
import serwio.company.model.ServiceCompany;
import serwio.company.repository.ServiceBranchRepository;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.security.CurrentUser;
import serwio.technician.repository.TechnicianRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCompanyService {
    private final ServiceCompanyRepository companies;
    private final ServiceBranchRepository branches;
    private final TechnicianRepository technicians;
    private final CurrentUser currentUser;

    public ServiceCompanyResponse getCurrent() {
        var company = currentCompany();
        return new ServiceCompanyResponse(company.getId(), company.getUser().getId(), company.getName(),
                company.getCreatedAt());
    }

    public CompanySummaryResponse getSummary() {
        var companyId = currentCompany().getId();
        var pending = technicians.countByBranchServiceCompanyIdAndActiveTrueAndUserPasswordChangeRequiredTrue(
                companyId);
        return new CompanySummaryResponse(
                branches.countByServiceCompanyIdAndActive(companyId, true),
                branches.countByServiceCompanyIdAndActive(companyId, false),
                technicians.countByBranchServiceCompanyIdAndActive(companyId, true) - pending,
                pending,
                technicians.countByBranchServiceCompanyIdAndActive(companyId, false));
    }

    private ServiceCompany currentCompany() {
        return companies.findByUserId(currentUser.id())
                .orElseThrow(() -> new ResourceNotFoundException("Konto nie ma profilu firmy serwisowej"));
    }
}
