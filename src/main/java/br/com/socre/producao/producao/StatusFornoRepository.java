package br.com.socre.producao.producao;

import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface StatusFornoRepository extends JpaRepository<StatusForno, UUID> {
    List<StatusForno> findByUnidadeIdOrderByDataHoraDescCriadoEmDesc(UUID unidade, Pageable pagina);
    @org.springframework.data.jpa.repository.Query("select s from StatusForno s join s.fornadas f where f.id = :fornada order by s.dataHora desc, s.criadoEm desc")
    List<StatusForno> daFornada(UUID fornada, Pageable pagina);
}
