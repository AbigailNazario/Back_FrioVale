package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "telemetry", indexes = {
        @Index(name = "idx_telemetry_trip_time", columnList = "trip_id, recorded_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(nullable = false)
    private Double temperature;

    private Double humidity;

    private Double latitude;

    private Double longitude;

    /** Momento da leitura (created_at do ThingSpeak, convertido para o fuso da aplicação). */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "is_alert", nullable = false)
    @Builder.Default
    private Boolean isAlert = Boolean.FALSE;

    /** entry_id do ThingSpeak: evita gravar a mesma leitura duas vezes entre polls. */
    @Column(name = "source_entry_id")
    private Long sourceEntryId;
}
