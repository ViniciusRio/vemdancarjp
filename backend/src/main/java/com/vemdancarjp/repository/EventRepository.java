package com.vemdancarjp.repository;

import com.vemdancarjp.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findAllByOrderBySortOrderAsc();

    List<Event> findAllByDayIdOrderBySortOrderAsc(String dayId);
}
