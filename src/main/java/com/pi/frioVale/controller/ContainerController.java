package com.pi.frioVale.controller;

import com.pi.frioVale.security.AccessGuard;
import com.pi.frioVale.dto.ContainerRequest;
import com.pi.frioVale.dto.ContainerResponse;
import com.pi.frioVale.service.ContainerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gerenciamento de contêineres refrigerados e seus dispositivos IoT (ThingSpeak). */
@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {

    private final ContainerService containerService;
    private final AccessGuard accessGuard; // [Seguranca - IDOR]

    @GetMapping
    public List<ContainerResponse> list() {
        return accessGuard.onlyOwnContainers(containerService.findAll(), ContainerResponse::id); // [Seguranca - IDOR]
    }

    @GetMapping("/{id}")
    public ContainerResponse getById(@PathVariable Long id) {
        accessGuard.requireContainer(id); // [Seguranca - IDOR]
        return containerService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContainerResponse create(
            @Valid @RequestBody ContainerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        accessGuard.requireCompany("container:create"); // [Seguranca - IDOR] registra o bloqueio no log
        return containerService.create(request, jwt);
    }

    @PutMapping("/{id}")
    public ContainerResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ContainerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        accessGuard.requireCompany("container:" + id + ":config"); // [Seguranca - IDOR] configuracao do dispositivo
        return containerService.update(id, request, jwt);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        accessGuard.requireCompany("container:" + id + ":delete"); // [Seguranca - IDOR]
        containerService.delete(id, jwt);
    }
}
