package br.com.socre.producao.acesso;

import java.io.Serializable;
import java.util.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Only immutable scalar data is kept in the persistent HTTP session.
public record UsuarioAutenticado(UUID id, String nome, String email, String password,
        Perfil perfil, UUID unidadeId, int versaoAcesso) implements UserDetails, Serializable {
    public static UsuarioAutenticado from(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getNome(), u.getEmail(), u.getSenhaHash(),
                u.getPerfil(), u.getUnidade() == null ? null : u.getUnidade().getId(), u.getVersaoAcesso());
    }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return password; }
    public String getNome() { return nome; }
    public Perfil getPerfil() { return perfil; }
    public UUID getId() { return id; }
}
