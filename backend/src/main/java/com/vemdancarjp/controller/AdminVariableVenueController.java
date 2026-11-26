package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vemdancarjp.dto.request.VariableVenueRequest;
import com.vemdancarjp.dto.response.AdminVariableVenueResponse;
import com.vemdancarjp.dto.response.CreatedResourceResponse;
import com.vemdancarjp.repository.VariableVenueRepository;
import com.vemdancarjp.service.VenueWriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.vemdancarjp.controller.AdminRequestValidation.optionalInstagram;
import static com.vemdancarjp.controller.AdminRequestValidation.rawId;
import static com.vemdancarjp.controller.AdminRequestValidation.requireObjectWithFields;
import static com.vemdancarjp.controller.AdminRequestValidation.requiredText;
import static com.vemdancarjp.controller.AdminRequestValidation.sortOrder;

@RestController
@RequestMapping("/api/admin/variable-venues")
public class AdminVariableVenueController {

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "neighborhood", "instagram", "days", "sortOrder");

    private final VenueWriteService venueWriteService;
    private final VariableVenueRepository variableVenueRepository;

    public AdminVariableVenueController(VenueWriteService venueWriteService,
                                        VariableVenueRepository variableVenueRepository) {
        this.venueWriteService = venueWriteService;
        this.variableVenueRepository = variableVenueRepository;
    }

    @GetMapping
    public List<AdminVariableVenueResponse> list() {
        return variableVenueRepository.findAllByOrderBySortOrderAsc().stream()
            .map(AdminVariableVenueResponse::new)
            .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResourceResponse create(@RequestBody JsonNode body) {
        return new CreatedResourceResponse(venueWriteService.createVariable(parse(body)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(HttpServletRequest httpRequest, @RequestBody JsonNode body) {
        Long id = rawId(httpRequest);
        VariableVenueRequest request = parse(body);
        return venueWriteService.updateVariable(id, request)
            .<ResponseEntity<?>>map(venue -> ResponseEntity.ok(new AdminVariableVenueResponse(venue)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest httpRequest) {
        venueWriteService.deleteVariable(rawId(httpRequest));
    }

    private static VariableVenueRequest parse(JsonNode body) {
        requireObjectWithFields(body, ALLOWED_FIELDS);
        int sortOrder = sortOrder(body);
        String instagram = optionalInstagram(body);
        return new VariableVenueRequest(
            requiredText(body, "name"),
            requiredText(body, "neighborhood"),
            instagram,
            requiredText(body, "days"),
            sortOrder);
    }
}
