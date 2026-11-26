package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vemdancarjp.dto.request.OtherVenueRequest;
import com.vemdancarjp.dto.response.AdminOtherVenueResponse;
import com.vemdancarjp.dto.response.CreatedResourceResponse;
import com.vemdancarjp.repository.OtherVenueRepository;
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
@RequestMapping("/api/admin/other-venues")
public class AdminOtherVenueController {

    private static final Set<String> ALLOWED_FIELDS = Set.of("name", "neighborhood", "instagram", "sortOrder");

    private final VenueWriteService venueWriteService;
    private final OtherVenueRepository otherVenueRepository;

    public AdminOtherVenueController(VenueWriteService venueWriteService,
                                        OtherVenueRepository otherVenueRepository) {
        this.venueWriteService = venueWriteService;
        this.otherVenueRepository = otherVenueRepository;
    }

    @GetMapping
    public List<AdminOtherVenueResponse> list() {
        return otherVenueRepository.findAllByOrderBySortOrderAsc().stream()
            .map(AdminOtherVenueResponse::new)
            .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResourceResponse create(@RequestBody JsonNode body) {
        return new CreatedResourceResponse(venueWriteService.createOther(parse(body)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(HttpServletRequest httpRequest, @RequestBody JsonNode body) {
        Long id = rawId(httpRequest);
        OtherVenueRequest request = parse(body);
        return venueWriteService.updateOther(id, request)
            .<ResponseEntity<?>>map(venue -> ResponseEntity.ok(new AdminOtherVenueResponse(venue)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest httpRequest) {
        venueWriteService.deleteOther(rawId(httpRequest));
    }

    private static OtherVenueRequest parse(JsonNode body) {
        requireObjectWithFields(body, ALLOWED_FIELDS);
        int sortOrder = sortOrder(body);
        String instagram = optionalInstagram(body);
        return new OtherVenueRequest(
            requiredText(body, "name"),
            requiredText(body, "neighborhood"),
            instagram,
            sortOrder);
    }
}
