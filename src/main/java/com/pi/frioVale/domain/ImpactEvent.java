package com.pi.frioVale.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Evento de impacto detectado pelo acelerômetro do dispositivo IoT.
 *
 * <p>O firmware publica os eventos no MESMO canal ThingSpeak do contêiner:
 * field1=800 (código do evento), field2=identificação da placa, field5=pico em g.
 * Códigos 840 (falha) e 841 (recuperação) são apenas registrados como logs brutos
 * ({@link ThingSpeakLog}) e não geram ImpactEvent.</p>
 *
 * <p>Independente da telemetria térmica: o impacto fica vinculado à viagem apenas
 * quando havia uma viagem em andamento no momento da ocorrência; caso contrário
 * o evento é preservado com {@code trip = null} (ainda assim consultável por contêiner).</p>
 */
@Entity
@Table(name = "impact_events",
        indexes = {
                @Index(name = "idx_impact_trip_time", columnList = "trip_id, original_timestamp"),
                @Index(name = "idx_impact_container", columnList = "container_id")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_impact_channel_entry",
                columnNames = {"channel_id", "entry_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Viagem em andamento no momento do impacto (nulo se não havia viagem confirmada). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "container_id", nullable = false)
    private Container container;

    @Column(name = "channel_id", nullable = false, length = 40)
    private String channelId;

    /** entry_id do ThingSpeak; garante idempotência no reprocessamento. */
    @Column(name = "entry_id")
    private Long entryId;

    /** Identificação da placa enviada no field2 pelo firmware. */
    @Column(length = 60)
    private String plate;

    /** Pico de aceleração lido no field5 (g). */
    @Column(name = "peak_g")
    private Double peakG;

    /**
     * Horário da ocorrência. O firmware não envia um campo próprio de timestamp,
     * então usamos o created_at do ThingSpeak (convertido para o fuso da aplicação)
     * como horário original da publicação/ocorrência.
     */
    @Column(name = "original_timestamp", nullable = false)
    private LocalDateTime originalTimestamp;

    /** Momento em que o backend recebeu/processou o evento. */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
