package com.pi.frioVale.dto;

import java.util.List;

/** [Seguranca - 2FA] Novos codigos de recuperacao; os anteriores deixam de valer. */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
