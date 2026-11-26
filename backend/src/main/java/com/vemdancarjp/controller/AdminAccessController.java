package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vemdancarjp.config.GoogleIdentity;
import com.vemdancarjp.dto.response.AdminAccessResponse;
import com.vemdancarjp.service.AdminAccessService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.vemdancarjp.controller.AdminRequestValidation.rawUuid;
import static com.vemdancarjp.controller.AdminRequestValidation.requireObjectWithFields;

@RestController
@RequestMapping("/api/admin/access")
public class AdminAccessController {

    private final AdminAccessService adminAccessService;

    public AdminAccessController(AdminAccessService adminAccessService) {
        this.adminAccessService = adminAccessService;
    }

    @GetMapping
    public List<AdminAccessResponse> list() {
        return adminAccessService.list();
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approve(HttpServletRequest request, @RequestBody JsonNode body,
                                     Authentication authentication) {
        UUID id = rawUuid(request);
        requireObjectWithFields(body, Set.of("status"));
        JsonNode status = body.get("status");
        if (status == null || !status.isTextual() || !"APPROVED".equals(status.textValue())) {
            throw new AdminRequestValidation.InvalidAdminRequestException();
        }
        return adminAccessService.approve(executor(authentication), id)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(HttpServletRequest request, Authentication authentication) {
        UUID id = rawUuid(request);
        adminAccessService.remove(executor(authentication), id);
    }

    @ExceptionHandler(AdminAccessService.ExecutorNotApprovedException.class)
    ResponseEntity<Map<String, String>> executorNotApproved() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN"));
    }

    @ExceptionHandler(AdminAccessService.CannotRemoveSelfException.class)
    ResponseEntity<Map<String, String>> cannotRemoveSelf() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CANNOT_REMOVE_SELF"));
    }

    private static String executor(Authentication authentication) {
        return GoogleIdentity.from(authentication).orElseThrow().getSubject();
    }
}
