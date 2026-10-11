package com.pi.frioVale.service;

import com.pi.frioVale.domain.Container;
import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.domain.Trip;
import com.pi.frioVale.domain.User;
import com.pi.frioVale.domain.enums.Role;
import com.pi.frioVale.domain.enums.TripStatus;
import com.pi.frioVale.dto.TripRequest;
import com.pi.frioVale.dto.TripResponse;
import com.pi.frioVale.exception.BusinessRuleException;
import com.pi.frioVale.exception.ResourceNotFoundException;
import com.pi.frioVale.repository.ContainerRepository;
import com.pi.frioVale.repository.FruitRepository;
import com.pi.frioVale.repository.TripRepository;
import com.pi.frioVale.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
