package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

/** Contêiner refrigerado com o dispositivo IoT associado a um canal do ThingSpeak. */
@Entity
@Table(name = "containers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identificador legível, ex.: "CONT-102". */
    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 40)
    private String thingSpeakChannelId;

    /** Read API Key do canal (segredo: nunca expor em DTOs/logs). */
    @Column(length = 100)
    private String thingSpeakApiKey;
}
