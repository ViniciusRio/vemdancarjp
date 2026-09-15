package com.vemdancarjp.repository;

import com.vemdancarjp.entity.VariableVenue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VariableVenueRepository extends JpaRepository<VariableVenue, Long> {

    List<VariableVenue> findAllByOrderBySortOrderAsc();
}
