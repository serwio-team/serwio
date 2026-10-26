package serwio.account.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserAccountResponse(UUID id, String email, String role, String status,
                                  boolean passwordChangeRequired, OffsetDateTime createdAt) {
}
