package com.lore.api.controller;

import com.lore.change.Change;
import com.lore.change.ChangeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ChangeController {

    private final ChangeRepository changeRepository;

    public ChangeController(ChangeRepository changeRepository) {
        this.changeRepository = changeRepository;
    }

    @PutMapping("/changes/{id}/understood")
    public Map<String, Boolean> toggleUnderstood(@PathVariable UUID id) {
        Change change = changeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        change.setUnderstood(!change.isUnderstood());
        changeRepository.save(change);

        return Map.of("understood", change.isUnderstood());
    }
}
