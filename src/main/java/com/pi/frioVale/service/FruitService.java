package com.pi.frioVale.service;

import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.dto.FruitRequest;
import com.pi.frioVale.dto.FruitResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.repository.FruitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FruitService {

    private final FruitRepository fruitRepository;

    @Transactional
    public FruitResponse create(FruitRequest request) {
        if (request.minTemp() >= request.maxTemp()) {
            throw new BusinessRuleException("minTemp deve ser menor que maxTemp", HttpStatus.BAD_REQUEST);
        }
        if (request.minHumidity() >= request.maxHumidity()) {
            throw new BusinessRuleException("minHumidity deve ser menor que maxHumidity", HttpStatus.BAD_REQUEST);
        }
        String name = request.name().trim();
        if (fruitRepository.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Já existe uma fruta cadastrada com o nome '" + name + "'");
        }

        Fruit fruit = Fruit.builder()
                .name(name)
                .minTemp(request.minTemp())
                .maxTemp(request.maxTemp())
                .minHumidity(request.minHumidity())
                .maxHumidity(request.maxHumidity())
                .build();
        return FruitResponse.from(fruitRepository.save(fruit));
    }

    @Transactional(readOnly = true)
    public List<FruitResponse> findAll() {
        return fruitRepository.findAll().stream().map(FruitResponse::from).toList();
    }
}
