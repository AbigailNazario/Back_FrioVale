package com.pi.frioVale.controller;

import com.pi.frioVale.dto.UserResponse;
import com.pi.frioVale.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Somente gestor (regra em SecurityConfig): usado para escolher o motorista ao criar uma viagem. */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/drivers")
    public List<UserResponse> drivers() {
        return userService.listDrivers();
    }
}
