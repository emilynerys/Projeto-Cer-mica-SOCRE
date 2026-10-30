package br.com.socre.producao.cadastro;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MotivoRepository extends JpaRepository<Motivo, UUID> {
    List<Motivo> findByTipoAndAtivoTrueOrderByDescricao(Motivo.Tipo tipo);
}
