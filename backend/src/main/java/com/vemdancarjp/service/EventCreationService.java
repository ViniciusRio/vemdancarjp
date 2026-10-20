package com.vemdancarjp.service;

import com.vemdancarjp.dto.request.CreateEventRequest;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventCreationService {

    private final EventRepository eventRepository;

    public EventCreationService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Long create(CreateEventRequest request) {
        Event event = new Event();
        event.setDayId(request.dayId());
        event.setName(request.name());
        event.setVenue(request.venue());
        event.setNeighborhood(request.neighborhood());
        event.setInstagram(request.instagram());
        event.setFrequency(request.frequency());
        event.setSortOrder(request.sortOrder());
        return eventRepository.save(event).getId();
    }
}
