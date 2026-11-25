package com.vemdancarjp.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vemdancarjp.dto.request.CreateEventRequest;
import com.vemdancarjp.dto.response.AdminEventResponse;
import com.vemdancarjp.dto.response.CreatedEventResponse;
import com.vemdancarjp.entity.DayOfWeek;
import com.vemdancarjp.repository.EventRepository;
import com.vemdancarjp.service.EventWriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/admin/events")
public class AdminEventController {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
        "dayId", "name", "venue", "neighborhood", "instagram", "frequency", "sortOrder");
    private static final Set<String> DAY_IDS = Set.copyOf(
        Arrays.stream(DayOfWeek.values()).map(DayOfWeek::id).toList());

    private static final Pattern EVENT_ID = Pattern.compile("[1-9][0-9]{0,18}");

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
                throw new InvalidEventRequestException();
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
    public CreatedEventResponse create(@RequestBody JsonNode body) {
        return new CreatedEventResponse(eventWriteService.create(parse(body)));
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") String id, @RequestBody JsonNode body) {
        Long eventId = parseId(id);
        CreateEventRequest request = parse(body);
        return eventWriteService.update(eventId, request)
            .<ResponseEntity<?>>map(event -> ResponseEntity.ok(new AdminEventResponse(event)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "NOT_FOUND")));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("id") String id) {
        eventWriteService.delete(parseId(id));
    }

    @ExceptionHandler({InvalidEventRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION_ERROR"));
    }

    private static Long parseId(String id) {
        if (!EVENT_ID.matcher(id).matches()) {
            throw new InvalidEventRequestException();
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException exception) {
            throw new InvalidEventRequestException();
        }
    }

    private static CreateEventRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new InvalidEventRequestException();
        }
        Iterator<String> fields = body.fieldNames();
        while (fields.hasNext()) {
            if (!ALLOWED_FIELDS.contains(fields.next())) {
                throw new InvalidEventRequestException();
            }
        }

        String dayId = requiredText(body, "dayId");
        if (dayId.length() > 10 || !DAY_IDS.contains(dayId)) {
            throw new InvalidEventRequestException();
        }
        JsonNode sortOrder = body.get("sortOrder");
        if (sortOrder == null || !sortOrder.isIntegralNumber() || !sortOrder.canConvertToInt()) {
            throw new InvalidEventRequestException();
        }

        String instagram = optionalText(body, "instagram");
        if (instagram != null && !validHttpUrl(instagram)) {
            throw new InvalidEventRequestException();
        }

        return new CreateEventRequest(
            dayId,
            requiredText(body, "name"),
            requiredText(body, "venue"),
            requiredText(body, "neighborhood"),
            instagram,
            optionalText(body, "frequency"),
            sortOrder.intValue());
    }

    private static String optionalText(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new InvalidEventRequestException();
        }
        String value = node.textValue().strip();
        if (value.indexOf('\0') >= 0) {
            throw new InvalidEventRequestException();
        }
        return value.isEmpty() ? null : value;
    }

    private static String requiredText(JsonNode body, String field) {
        String value = optionalText(body, field);
        if (value == null) {
            throw new InvalidEventRequestException();
        }
        return value;
    }

    private static boolean validHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return uri.isAbsolute() && uri.getHost() != null &&
                ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static class InvalidEventRequestException extends RuntimeException {
    }
}
