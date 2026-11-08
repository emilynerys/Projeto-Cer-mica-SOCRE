package br.com.socre.producao.cadastro;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UnidadeRepository extends JpaRepository<Unidade, UUID> {
    List<Unidade> findAllByOrderByNome();
    /** The unit of whoever registers: the Registro's own, or the single active unit for the other profiles (Carolina for now). */
    default Unidade deTrabalho(UUID daConta) {
        if (daConta != null) return findById(daConta).orElseThrow();
        return findAllByOrderByNome().stream().filter(Unidade::isAtiva).findFirst().orElseThrow(() -> new IllegalStateException("Nenhuma unidade ativa cadastrada."));
    }
}
