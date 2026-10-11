package com.pi.frioVale.dto;

import com.pi.frioVale.domain.Fruit;

public record FruitResponse(
        Long id,
        String name,
        Double minTemp,
        Double maxTemp,
        Double minHumidity,
        Double maxHumidity
) {
    public static FruitResponse from(Fruit f) {
        return new FruitResponse(f.getId(), f.getName(), f.getMinTemp(), f.getMaxTemp(),
                f.getMinHumidity(), f.getMaxHumidity());
    }
}
