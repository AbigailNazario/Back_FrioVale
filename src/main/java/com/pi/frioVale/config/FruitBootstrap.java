package com.pi.frioVale.config;

import com.pi.frioVale.domain.Fruit;
import com.pi.frioVale.repository.FruitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Garante que as frutas padrão existam no banco com suas faixas seguras.
 * - Uva (mesa): 2 °C a 8 °C
 * - Manga (madura): 10 °C a 13 °C
 *
 * Só cria quando a fruta ainda não existe (busca case-insensitive por nome),
 * preservando qualquer fruta cadastrada pelo usuário.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FruitBootstrap implements ApplicationRunner {

    private final FruitRepository fruitRepository;

    @Override
    public void run(ApplicationArguments args) {
        criarSeNaoExistir("Uva", 2.0, 8.0, 85.0, 95.0);
        criarSeNaoExistir("Manga", 10.0, 13.0, 85.0, 95.0);
    }

    private void criarSeNaoExistir(String nome, Double minTemp, Double maxTemp,
                                    Double minHumidity, Double maxHumidity) {
        if (fruitRepository.existsByNameIgnoreCase(nome)) {
            return;
        }
        Fruit fruit = Fruit.builder()
                .name(nome)
                .minTemp(minTemp)
                .maxTemp(maxTemp)
                .minHumidity(minHumidity)
                .maxHumidity(maxHumidity)
                .build();
        fruitRepository.save(fruit);
        log.info("Fruta padrão criada: {} ({} °C a {} °C)", nome, minTemp, maxTemp);
    }
}
