package serwio.company.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.company.dto.ServiceCompanyResponse;
import serwio.company.repository.ServiceCompanyRepository;
import serwio.exception.ResourceNotFoundException;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCompanyService {
    private final ServiceCompanyRepository companies;
    private final CurrentUser currentUser;

    public ServiceCompanyResponse getCurrent() {
        var userId = currentUser.id();
        var company = companies.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Konto nie ma profilu firmy serwisowej"));
        return new ServiceCompanyResponse(company.getId(), userId, company.getName(), company.getCreatedAt());
    }
}
