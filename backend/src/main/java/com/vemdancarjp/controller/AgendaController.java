package com.vemdancarjp.controller;

import com.vemdancarjp.dto.response.AgendaResponse;
import com.vemdancarjp.service.AgendaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agenda")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping
    public AgendaResponse getAgenda() {
        return agendaService.getAgenda();
    }
}
