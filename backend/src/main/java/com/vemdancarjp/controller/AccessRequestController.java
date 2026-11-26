package com.vemdancarjp.controller;

import com.vemdancarjp.config.GoogleIdentity;
import com.vemdancarjp.service.AdminAccessService;
import com.vemdancarjp.service.AdminAccessService.AccessRequestResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AccessRequestController {

    private final AdminAccessService adminAccessService;

    public AccessRequestController(AdminAccessService adminAccessService) {
        this.adminAccessService = adminAccessService;
    }

    @PostMapping("/api/auth/access-request")
    public ResponseEntity<Map<String, String>> request(Authentication authentication) {
        OidcUser user = GoogleIdentity.from(authentication).orElseThrow();
        boolean emailVerified = user.getClaims().get("email_verified") instanceof Boolean verified && verified;
        AccessRequestResult result = adminAccessService.request(
            user.getSubject(), user.getClaimAsString("email"), emailVerified, user.getClaimAsString("name"));
        return switch (result.outcome()) {
            case CREATED -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("accessStatus", result.accessStatus()));
            case EXISTING -> ResponseEntity.ok(Map.of("accessStatus", result.accessStatus()));
            case EMAIL_NOT_VERIFIED -> ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "EMAIL_NOT_VERIFIED"));
        };
    }
}
