package serwio.company.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import serwio.company.dto.ServiceCompanyResponse;
import serwio.company.service.ServiceCompanyService;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class ServiceCompanyController {
    private final ServiceCompanyService service;

    @GetMapping("/me")
    public ServiceCompanyResponse current() {
        return service.getCurrent();
    }
}
