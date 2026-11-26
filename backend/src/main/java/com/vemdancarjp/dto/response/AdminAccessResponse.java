package com.vemdancarjp.dto.response;

import java.time.Instant;
import java.util.UUID;

public record AdminAccessResponse(UUID id, String email, String name, String status, Instant requestedAt) {
}
