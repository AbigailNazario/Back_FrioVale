package com.pi.frioVale.domain;

import com.pi.frioVale.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

/** Usuário do sistema (motorista ou empresa/gestor). Tabela "users" para evitar conflito com palavra reservada. */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    /** Hash BCrypt, nunca a senha em texto puro. */
    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;
}
