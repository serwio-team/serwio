package serwio.owner.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import serwio.exception.ResourceNotFoundException;
import serwio.owner.dto.AssetOwnerResponse;
import serwio.owner.repository.AssetOwnerRepository;
import serwio.security.CurrentUser;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetOwnerService {
    private final AssetOwnerRepository owners;
    private final CurrentUser currentUser;

    public AssetOwnerResponse getCurrent() {
        var userId = currentUser.id();
        var owner = owners.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Konto nie ma profilu właściciela"));
        return new AssetOwnerResponse(owner.getId(), userId, owner.getName(), owner.getCreatedAt());
    }
}
