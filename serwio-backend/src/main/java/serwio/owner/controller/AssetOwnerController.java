package serwio.owner.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import serwio.owner.dto.AssetOwnerResponse;
import serwio.owner.service.AssetOwnerService;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class AssetOwnerController {
    private final AssetOwnerService service;

    @GetMapping("/me")
    public AssetOwnerResponse current() {
        return service.getCurrent();
    }
}
