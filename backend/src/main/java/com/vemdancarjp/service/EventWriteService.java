package com.vemdancarjp.service;

import com.vemdancarjp.dto.request.CreateEventRequest;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class EventWriteService {

    private final EventRepository eventRepository;

    public EventWriteService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Long create(CreateEventRequest request) {
        Event event = new Event();
        apply(event, request);
        return eventRepository.save(event).getId();
    }

    @Transactional
    public Optional<Event> update(Long id, CreateEventRequest request) {
        return eventRepository.findById(id).map(event -> {
            apply(event, request);
            return eventRepository.saveAndFlush(event);
        });
    }

    @Transactional
    public void delete(Long id) {
        eventRepository.deleteAllByIdInBatch(List.of(id));
    }

    private static void apply(Event event, CreateEventRequest request) {
        event.setDayId(request.dayId());
        event.setName(request.name());
        event.setVenue(request.venue());
        event.setNeighborhood(request.neighborhood());
        event.setInstagram(request.instagram());
        event.setFrequency(request.frequency());
        event.setSortOrder(request.sortOrder());
    }
}
