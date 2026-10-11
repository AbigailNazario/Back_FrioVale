package com.pi.frioVale.repository;

import com.pi.frioVale.domain.Fruit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FruitRepository extends JpaRepository<Fruit, Long> {
    boolean existsByNameIgnoreCase(String name);
}
