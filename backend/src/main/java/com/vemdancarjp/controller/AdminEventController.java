package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vemdancarjp.dto.request.CreateEventRequest;
import com.vemdancarjp.dto.response.AdminEventResponse;
import com.vemdancarjp.dto.response.CreatedResourceResponse;
import com.vemdancarjp.entity.DayOfWeek;
import com.vemdancarjp.repository.EventRepository;
import com.vemdancarjp.service.EventWriteService;
import jakarta.servlet.http.HttpServletRequest;
import com.vemdancarjp.controller.AdminRequestValidation.InvalidAdminRequestException;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.vemdancarjp.controller.AdminRequestValidation.optionalInstagram;
import static com.vemdancarjp.controller.AdminRequestValidation.optionalText;
import static com.vemdancarjp.controller.AdminRequestValidation.rawId;
import static com.vemdancarjp.controller.AdminRequestValidation.requireObjectWithFields;
import static com.vemdancarjp.controller.AdminRequestValidation.requiredText;
import static com.vemdancarjp.controller.AdminRequestValidation.sortOrder;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "dayId", "name", "venue", "neighborhood", "instagram", "frequency", "sortOrder");
    private static final Set<String> DAY_IDS = Set.copyOf(
        Arrays.stream(DayOfWeek.values()).map(DayOfWeek::id).toList());

    private final EventWriteService eventWriteService;
    private final EventRepository eventRepository;

    public AdminEventController(EventWriteService eventWriteService, EventRepository eventRepository) {
        this.eventWriteService = eventWriteService;
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public List<AdminEventResponse> list(HttpServletRequest request) {
        String[] dayIds = request.getParameterValues("dayId");
        if (dayIds != null) {
            if (dayIds.length != 1 || !DAY_IDS.contains(dayIds[0])) {
                throw new InvalidAdminRequestException();
            }
            return eventRepository.findAllByDayIdOrderBySortOrderAsc(dayIds[0]).stream()
                .map(AdminEventResponse::new)
                .toList();
        }

        var events = eventRepository.findAllByOrderBySortOrderAsc();
        return Arrays.stream(DayOfWeek.values())
            .flatMap(day -> events.stream().filter(event -> day.id().equals(event.getDayId())))
            .map(AdminEventResponse::new)
            .toList();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedResourceResponse create(@RequestBody JsonNode body) {
        return new CreatedResourceResponse(eventWriteService.create(parse(body)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(HttpServletRequest httpRequest, @RequestBody JsonNode body) {
        Long eventId = rawId(httpRequest);
        CreateEventRequest request = parse(body);
        return eventWriteService.update(eventId, request)
            .<ResponseEntity<?>>map(event -> ResponseEntity.ok(new AdminEventResponse(event)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(HttpServletRequest httpRequest) {
        eventWriteService.delete(rawId(httpRequest));
    }

    private static CreateEventRequest parse(JsonNode body) {
        requireObjectWithFields(body, ALLOWED_FIELDS);
        String dayId = requiredText(body, "dayId");
        if (dayId.length() > 10 || !DAY_IDS.contains(dayId)) {
            throw new InvalidAdminRequestException();
        }
        int sortOrder = sortOrder(body);
        String instagram = optionalInstagram(body);

        return new CreateEventRequest(
            dayId,
            requiredText(body, "name"),
            requiredText(body, "venue"),
            requiredText(body, "neighborhood"),
            instagram,
            optionalText(body, "frequency"),
            sortOrder);
    }
}
