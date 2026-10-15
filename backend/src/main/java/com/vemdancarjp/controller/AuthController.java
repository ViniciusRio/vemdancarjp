package com.vemdancarjp.controller;

import com.vemdancarjp.repository.AdminAccessRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    private final AdminAccessRepository adminAccessRepository;

    public AuthController(AdminAccessRepository adminAccessRepository) {
        this.adminAccessRepository = adminAccessRepository;
    }

    @GetMapping("/api/auth/session")
    public ResponseEntity<Map<String, Object>> session(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore())
                .body(Map.of("error", "UNAUTHENTICATED"));
        }
        String status = adminAccessRepository.findByGoogleSub(oidcUser.getSubject())
            .map(access -> access.getStatus().name())
            .orElse("NONE");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("authenticated", true, "accessStatus", status));
    }

    @GetMapping("/api/auth/csrf")
    public ResponseEntity<Map<String, String>> csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(Map.of("token", csrfToken.getToken()));
    }
}
