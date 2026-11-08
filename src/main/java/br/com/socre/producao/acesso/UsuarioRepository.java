package br.com.socre.producao.acesso;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByLoginIgnoreCase(String login);
    List<Usuario> findAllByOrderByNome();
}
