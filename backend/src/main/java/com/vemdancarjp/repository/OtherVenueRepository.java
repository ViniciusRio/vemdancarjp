package com.vemdancarjp.repository;

import com.vemdancarjp.entity.OtherVenue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OtherVenueRepository extends JpaRepository<OtherVenue, Long> {

    List<OtherVenue> findAllByOrderBySortOrderAsc();
}
