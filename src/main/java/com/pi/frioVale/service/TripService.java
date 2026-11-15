package com.pi.frioVale.service;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.Telemetry;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.TripMonitorResponse;
import com.pi.frioVale.dto.TripRequest;
import com.pi.frioVale.dto.TripResponse;
import com.pi.frioVale.dto.TripUpdateRequest;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.IaPredictionRepository;
import com.pi.frioVale.repository.TelemetryRepository;
import com.pi.frioVale.repository.TripRepository;
import com.pi.frioVale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final FruitRepository fruitRepository;
    private final TelemetryRepository telemetryRepository;
    private final IaPredictionRepository iaPredictionRepository;
    private final Clock clock;

    /** Cadastra a viagem e, por padrão, já a inicia (IN_TRANSIT). */
    @Transactional
    public TripResponse create(TripRequest request) {
        Container container = containerRepository.findById(request.containerId())
                .orElseThrow(() -> new ResourceNotFoundException("Contêiner", request.containerId()));
        User driver = userRepository.findById(request.driverId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", request.driverId()));
        Fruit fruit = fruitRepository.findById(request.fruitId())
                .orElseThrow(() -> new ResourceNotFoundException("Fruta", request.fruitId()));

        if (driver.getRole() != Role.ROLE_DRIVER) {
            throw new BusinessRuleException("O usuário informado não é um motorista", HttpStatus.BAD_REQUEST);
        }

        Trip trip = Trip.builder()
                .container(container)
                .driver(driver)
                .fruit(fruit)
                .origin(normalize(request.origin()))
                .destination(normalize(request.destination()))
                .status(TripStatus.PLANNED)
                .build();

        if (request.shouldStartNow()) {
            applyStart(trip);
        }
        return TripResponse.from(tripRepository.save(trip));
    }

    /** PLANNED -> IN_TRANSIT */
    @Transactional
    public TripResponse startTrip(Long tripId) {
        Trip trip = getTrip(tripId);
        if (trip.getStatus() != TripStatus.PLANNED) {
            throw new BusinessRuleException("Só é possível iniciar viagens PLANNED (status atual: " + trip.getStatus() + ")");
        }
        applyStart(trip);
        return TripResponse.from(trip);
    }

    /** Altera o status da viagem, respeitando as regras de transição. */
    @Transactional
    public TripResponse updateStatus(Long id, TripStatus status) {
        return updateTrip(id, new TripUpdateRequest(status, null, null, null, null, null, null));
    }

    /**
     * Atualiza campos da viagem e o status, respeitando as regras de transição.
     * Campos omitidos (null) mantêm o valor atual.
     */
    @Transactional
    public TripResponse updateTrip(Long id, TripUpdateRequest request) {
        Trip trip = getTrip(id);
        TripStatus current = trip.getStatus();
        TripStatus next = request.status();

        if (current == next) {
            // ainda assim permite atualizar outros campos sem mudar o status
        } else {
            switch (next) {
                case IN_TRANSIT -> {
                    if (current != TripStatus.PLANNED) {
                        throw new BusinessRuleException("Só é possível iniciar viagens PLANNED (status atual: " + current + ")");
                    }
                    applyStart(trip);
                }
                case COMPLETED -> {
                    if (current != TripStatus.IN_TRANSIT) {
                        throw new BusinessRuleException("Só é possível finalizar viagens IN_TRANSIT (status atual: " + current + ")");
                    }
                    trip.setStatus(TripStatus.COMPLETED);
                    trip.setEndDate(LocalDateTime.now(clock));
                }
                case PLANNED -> {
                    trip.setStatus(TripStatus.PLANNED);
                    trip.setStartDate(null);
                    trip.setEndDate(null);
                }
            }
        }

        // Atualiza outros campos quando fornecidos
        if (request.containerId() != null) {
            Container container = containerRepository.findById(request.containerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contêiner", request.containerId()));
            trip.setContainer(container);
        }
        if (request.driverId() != null) {
            User driver = userRepository.findById(request.driverId())
                    .orElseThrow(() -> new ResourceNotFoundException("Usuário", request.driverId()));
            if (driver.getRole() != Role.ROLE_DRIVER) {
                throw new BusinessRuleException("O usuário informado não é um motorista", HttpStatus.BAD_REQUEST);
            }
            trip.setDriver(driver);
        }
        if (request.fruitId() != null) {
            Fruit fruit = fruitRepository.findById(request.fruitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fruta", request.fruitId()));
            trip.setFruit(fruit);
        }
        if (request.origin() != null) {
            trip.setOrigin(normalize(request.origin()));
        }
        if (request.destination() != null) {
            trip.setDestination(normalize(request.destination()));
        }

        // Se status for IN_TRANSIT e startNow explicitamente true, garante startDate
        if (next == TripStatus.IN_TRANSIT && Boolean.TRUE.equals(request.startNow()) && trip.getStartDate() == null) {
            trip.setStartDate(LocalDateTime.now(clock));
        }

        return TripResponse.from(tripRepository.save(trip));
    }

    /** IN_TRANSIT -> COMPLETED */
    @Transactional
    public TripResponse endTrip(Long tripId) {
        Trip trip = getTrip(tripId);
        if (trip.getStatus() != TripStatus.IN_TRANSIT) {
            throw new BusinessRuleException("Só é possível finalizar viagens IN_TRANSIT (status atual: " + trip.getStatus() + ")");
        }
        trip.setStatus(TripStatus.COMPLETED);
        trip.setEndDate(LocalDateTime.now(clock));
        return TripResponse.from(trip);
    }

    @Transactional(readOnly = true)
    public List<TripResponse> findActive() {
        return tripRepository.findByStatus(TripStatus.IN_TRANSIT).stream().map(TripResponse::from).toList();
    }

    /** Viagens ativas com container/fruta/motorista já carregados (uso do agendador, fora de transação). */
    @Transactional(readOnly = true)
    public List<Trip> findActiveTripsForPolling() {
        return tripRepository.findByStatus(TripStatus.IN_TRANSIT);
    }

    /** Todas as viagens com a última leitura e indicadores calculados a partir do banco. */
    @Transactional(readOnly = true)
    public List<TripMonitorResponse> findAllMonitor() {
        return tripRepository.findAllDetailed().stream().map(this::toMonitor).toList();
    }

    /** Apenas viagens em andamento, no formato consumido pela visão geral. */
    @Transactional(readOnly = true)
    public List<TripMonitorResponse> findActiveMonitor() {
        return tripRepository.findByStatus(TripStatus.IN_TRANSIT).stream().map(this::toMonitor).toList();
    }

    /**
     * Verifica se o usuário pode acessar a viagem.
     * Empresa (isCompany = true) acessa qualquer viagem; motorista acessa apenas as próprias.
     */
    @Transactional(readOnly = true)
    public boolean canAccessTrip(Long tripId, Long userId, boolean isCompany) {
        if (isCompany) {
            return tripRepository.existsById(tripId);
        }
        return tripRepository.findDetailedById(tripId)
                .filter(trip -> trip.getDriver() != null)
                .map(trip -> trip.getDriver().getId().equals(userId))
                .orElse(false);
    }

    private TripMonitorResponse toMonitor(Trip trip) {
        Long tripId = trip.getId();
        Fruit fruit = trip.getFruit();
        Telemetry latest = telemetryRepository.findFirstByTripIdOrderByTimestampDesc(tripId).orElse(null);
        long readings = telemetryRepository.countByTripId(tripId);
        long readingsInRange = telemetryRepository.countInRangeByTripId(
                tripId, fruit.getMinTemp(), fruit.getMaxTemp());
        Integer inRangePercent = readings == 0
                ? null
                : (int) Math.round(readingsInRange * 100.0 / readings);
        Boolean inRange = null;
        Boolean regrasAnomalia = null;
        if (latest != null && latest.getTemperature() != null) {
            double temperature = latest.getTemperature();
            inRange = temperature >= fruit.getMinTemp() && temperature <= fruit.getMaxTemp();
            regrasAnomalia = !inRange;
        }

        // Buscar resultado da IA salvo para esta leitura (sem chamar a API novamente)
        Boolean iaAnomalia = null;
        String iaResultado = null;
        String iaMensagem = null;
        String iaPerfil = null;
        Boolean iaDivergencia = null;
        if (latest != null) {
            var iaResult = iaPredictionRepository.findByTelemetryId(latest.getId());
            if (iaResult.isPresent()) {
                var r = iaResult.get();
                iaAnomalia = r.getIaAnomalia();
                iaResultado = r.getIaResultado();
                iaMensagem = r.getIaMensagem();
                iaPerfil = r.getIaPerfil();
                // Divergencia: IA e regras discordam
                if (regrasAnomalia != null && iaAnomalia != null) {
                    iaDivergencia = !regrasAnomalia.equals(iaAnomalia);
                }
            }
        }

        return new TripMonitorResponse(
                tripId,
                trip.getContainer().getId(), trip.getContainer().getCode(),
                trip.getDriver().getId(), trip.getDriver().getName(),
                fruit.getId(), fruit.getName(),
                fruit.getMinTemp(), fruit.getMaxTemp(),
                fruit.getMinHumidity(), fruit.getMaxHumidity(),
                trip.getOrigin(), trip.getDestination(),
                trip.getStatus(), trip.getStartDate(), trip.getEndDate(),
                latest == null ? null : latest.getTemperature(),
                latest == null ? null : latest.getHumidity(),
                latest == null ? null : latest.getLatitude(),
                latest == null ? null : latest.getLongitude(),
                latest == null ? null : latest.getTimestamp(),
                inRange, readings, readingsInRange, inRangePercent,
                iaAnomalia, iaResultado, iaMensagem, iaPerfil,
                regrasAnomalia, iaDivergencia);
    }

    private Trip getTrip(Long id) {
        return tripRepository.findDetailedById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem", id));
    }

    private void applyStart(Trip trip) {
        Long containerId = trip.getContainer().getId();
        if (tripRepository.existsByContainerIdAndStatus(containerId, TripStatus.IN_TRANSIT)) {
            throw new BusinessRuleException("O contêiner " + trip.getContainer().getCode()
                    + " já possui uma viagem em andamento");
        }
        trip.setStatus(TripStatus.IN_TRANSIT);
        trip.setStartDate(LocalDateTime.now(clock));
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
