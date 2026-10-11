package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

/** Tipo de fruta transportada e suas faixas seguras de temperatura e umidade. */
@Entity
@Table(name = "fruits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Fruit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(nullable = false)
    private Double minTemp;

    @Column(nullable = false)
    private Double maxTemp;

    @Column(nullable = false)
    private Double minHumidity;

    @Column(nullable = false)
    private Double maxHumidity;
}
