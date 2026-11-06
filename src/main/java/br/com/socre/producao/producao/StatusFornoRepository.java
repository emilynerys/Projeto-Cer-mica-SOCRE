package br.com.socre.producao.producao;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface StatusFornoRepository extends JpaRepository<StatusForno, UUID> {
    List<StatusForno> findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(UUID unidade, Pageable pagina);
}
