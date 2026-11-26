package com.vemdancarjp.service;

import com.vemdancarjp.dto.request.OtherVenueRequest;
import com.vemdancarjp.dto.request.VariableVenueRequest;
import com.vemdancarjp.entity.OtherVenue;
import com.vemdancarjp.entity.VariableVenue;
import com.vemdancarjp.repository.OtherVenueRepository;
import com.vemdancarjp.repository.VariableVenueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class VenueWriteService {

    private final VariableVenueRepository variableVenueRepository;
    private final OtherVenueRepository otherVenueRepository;

    public VenueWriteService(VariableVenueRepository variableVenueRepository,
                             OtherVenueRepository otherVenueRepository) {
        this.variableVenueRepository = variableVenueRepository;
        this.otherVenueRepository = otherVenueRepository;
    }

    @Transactional
    public Long createVariable(VariableVenueRequest request) {
        VariableVenue venue = new VariableVenue();
        apply(venue, request);
        return variableVenueRepository.save(venue).getId();
    }

    @Transactional
    public Optional<VariableVenue> updateVariable(Long id, VariableVenueRequest request) {
        return variableVenueRepository.findById(id).map(venue -> {
            apply(venue, request);
            return variableVenueRepository.saveAndFlush(venue);
        });
    }

    @Transactional
    public void deleteVariable(Long id) {
        variableVenueRepository.deleteAllByIdInBatch(List.of(id));
    }

    @Transactional
    public Long createOther(OtherVenueRequest request) {
        OtherVenue venue = new OtherVenue();
        apply(venue, request);
        return otherVenueRepository.save(venue).getId();
    }

    @Transactional
    public Optional<OtherVenue> updateOther(Long id, OtherVenueRequest request) {
        return otherVenueRepository.findById(id).map(venue -> {
            apply(venue, request);
            return otherVenueRepository.saveAndFlush(venue);
        });
    }

    @Transactional
    public void deleteOther(Long id) {
        otherVenueRepository.deleteAllByIdInBatch(List.of(id));
    }

    private static void apply(VariableVenue venue, VariableVenueRequest request) {
        venue.setName(request.name());
        venue.setNeighborhood(request.neighborhood());
        venue.setInstagram(request.instagram());
        venue.setDays(request.days());
        venue.setSortOrder(request.sortOrder());
    }

    private static void apply(OtherVenue venue, OtherVenueRequest request) {
        venue.setName(request.name());
        venue.setNeighborhood(request.neighborhood());
        venue.setInstagram(request.instagram());
        venue.setSortOrder(request.sortOrder());
    }
}
