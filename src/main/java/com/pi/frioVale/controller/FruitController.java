package com.pi.frioVale.controller;

import com.pi.frioVale.dto.FruitRequest;
import com.pi.frioVale.dto.FruitResponse;
import com.pi.frioVale.service.FruitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fruits")
@RequiredArgsConstructor
public class FruitController {

    private final FruitService fruitService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FruitResponse create(@Valid @RequestBody FruitRequest request) {
        return fruitService.create(request);
    }

    @GetMapping
    public List<FruitResponse> list() {
        return fruitService.findAll();
    }
}
