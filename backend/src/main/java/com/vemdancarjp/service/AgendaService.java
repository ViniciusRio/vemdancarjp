package com.vemdancarjp.service;

import com.vemdancarjp.config.AgendaProperties;
import com.vemdancarjp.dto.response.AgendaResponse;
import com.vemdancarjp.dto.response.DayResponse;
import com.vemdancarjp.dto.response.EventResponse;
import com.vemdancarjp.dto.response.OtherVenueResponse;
import com.vemdancarjp.dto.response.VariableVenueResponse;
import com.vemdancarjp.entity.DayOfWeek;
import com.vemdancarjp.entity.Event;
import com.vemdancarjp.entity.OtherVenue;
import com.vemdancarjp.entity.VariableVenue;
import com.vemdancarjp.repository.EventRepository;
import com.vemdancarjp.repository.OtherVenueRepository;
import com.vemdancarjp.repository.VariableVenueRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AgendaService {

    private final AgendaProperties properties;
    private final EventRepository eventRepository;
    private final VariableVenueRepository variableVenueRepository;
    private final OtherVenueRepository otherVenueRepository;

    public AgendaService(AgendaProperties properties,
                         EventRepository eventRepository,
                         VariableVenueRepository variableVenueRepository,
                         OtherVenueRepository otherVenueRepository) {
        this.properties = properties;
        this.eventRepository = eventRepository;
        this.variableVenueRepository = variableVenueRepository;
        this.otherVenueRepository = otherVenueRepository;
    }

    public AgendaResponse getAgenda() {
        List<Event> events = eventRepository.findAllByOrderBySortOrderAsc();
        List<VariableVenue> variableVenues = variableVenueRepository.findAllByOrderBySortOrderAsc();
        List<OtherVenue> otherVenues = otherVenueRepository.findAllByOrderBySortOrderAsc();

        List<DayResponse> days = List.of(DayOfWeek.values()).stream()
            .map(day -> new DayResponse(
                day.id(),
                day.label(),
                events.stream()
                    .filter(e -> e.getDayId().equals(day.id()))
                    .map(e -> new EventResponse(
                        e.getName(),
                        e.getVenue(),
                        e.getNeighborhood(),
                        e.getInstagram(),
                        e.getFrequency()
                    ))
                    .toList()
            ))
            .toList();

        List<VariableVenueResponse> variableVenueResponses = variableVenues.stream()
            .map(v -> new VariableVenueResponse(
                v.getName(),
                v.getNeighborhood(),
                v.getDays(),
                v.getInstagram()
            ))
            .toList();

        List<OtherVenueResponse> otherVenueResponses = otherVenues.stream()
            .map(v -> new OtherVenueResponse(
                v.getName(),
                v.getNeighborhood(),
                v.getInstagram()
            ))
            .toList();

        return new AgendaResponse(
            properties.city(),
            properties.title(),
            properties.subtitle(),
            LocalDate.now().toString(),
            days,
            variableVenueResponses,
            otherVenueResponses
        );
    }
}
