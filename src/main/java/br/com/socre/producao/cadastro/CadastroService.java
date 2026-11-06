package br.com.socre.producao.cadastro;

import br.com.socre.producao.acesso.*;
import br.com.socre.producao.producao.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// What the Administrador maintains: accounts (RF22, RF32) and reference values (RF26, RN13); every change goes to the audit trail.
@Service
public class CadastroService {
    private static final int SENHA_MINIMA = 8, SENHA_MAXIMA = 72, LINHAS_MAXIMAS = 500;
    private final UsuarioRepository usuarios; private final UnidadeRepository unidades; private final FormatoRepository formatos;
    private final MotivoRepository motivos; private final PasswordEncoder encoder; private final RegistroApoio apoio;
    public CadastroService(UsuarioRepository usuarios, UnidadeRepository unidades, FormatoRepository formatos, MotivoRepository motivos,
            PasswordEncoder encoder, RegistroApoio apoio) {
        this.usuarios = usuarios; this.unidades = unidades; this.formatos = formatos; this.motivos = motivos; this.encoder = encoder; this.apoio = apoio;
    }
    public record UsuarioForm(String nome, String email, Perfil perfil, UUID idUnidade, Boolean ativo, String senha) {}
    public record FormatoForm(String nome, Integer tijolosPorLinha, Boolean ativo) {}
    public record MotivoForm(Motivo.Tipo tipo, String descricao, Boolean exigeTexto) {}

    @Transactional
    public Usuario criarUsuario(UsuarioAutenticado p, UsuarioForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        apoio.travar("usuario-email");
        var email = email(f.email(), null);
        var nome = nome(f.nome());
        if (f.perfil() == null) throw new RegraNegocioException("Escolha o perfil de acesso.");
        conferirSenha(f.senha());
        var u = usuarios.saveAndFlush(new Usuario(UUID.randomUUID(), nome, email, encoder.encode(f.senha()), f.perfil(), unidadeDoPerfil(f.perfil(), f.idUnidade())));
        apoio.auditar("usuario", u.getId(), admin, "CRIACAO", null, retrato(u), null, null);
        return u;
    }
    @Transactional
    public Usuario editarUsuario(UsuarioAutenticado p, UUID id, UsuarioForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        apoio.travar("usuario-email");
        var u = usuario(id);
        var email = email(f.email(), id);
        var nome = nome(f.nome());
        if (f.perfil() == null) throw new RegraNegocioException("Escolha o perfil de acesso.");
        boolean ativo = Boolean.TRUE.equals(f.ativo());
        // Only an Administrador reaches this screen, so this also guarantees that one always remains.
        if (u.getId().equals(admin.getId()) && (f.perfil() != u.getPerfil() || !ativo))
            throw new RegraNegocioException("Você não pode mudar o próprio perfil nem desativar a própria conta.");
        String antes = retrato(u);
        u.editar(nome, email, f.perfil(), unidadeDoPerfil(f.perfil(), f.idUnidade()), ativo);
        usuarios.flush();
        apoio.auditar("usuario", u.getId(), admin, "EDICAO", antes, retrato(u), null, null);
        return u;
    }
    /** RF32: the new hash also ends the user's open sessions; the trail never holds the password. */
    @Transactional
    public Usuario redefinirSenha(UsuarioAutenticado p, UUID id, String senha) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        var u = usuario(id);
        conferirSenha(senha);
        u.redefinirSenha(encoder.encode(senha));
        usuarios.flush();
        apoio.auditar("usuario", u.getId(), admin, "SENHA_REDEFINIDA", null, retrato(u), null, null);
        return u;
    }
    public Usuario usuario(UUID id) { return usuarios.findById(id).orElseThrow(() -> new RegraNegocioException("Usuário não encontrado.")); }

    @Transactional
    public Formato criarFormato(UsuarioAutenticado p, FormatoForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        apoio.travar("formato-nome");
        var novo = formatos.saveAndFlush(new Formato(UUID.randomUUID(), nomeFormato(f.nome(), null), tijolosPorLinha(f.tijolosPorLinha())));
        apoio.auditar("formato", novo.getId(), admin, "CRIACAO", null, retrato(novo), null, null);
        return novo;
    }
    /** RN13: the change applies to the next records; the ones already saved keep their own values. */
    @Transactional
    public Formato editarFormato(UsuarioAutenticado p, UUID id, FormatoForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        apoio.travar("formato-nome");
        var formato = formato(id);
        var nome = nomeFormato(f.nome(), id);
        int porLinha = tijolosPorLinha(f.tijolosPorLinha());
        boolean ativo = Boolean.TRUE.equals(f.ativo());
        if (formato.isAtivo() && !ativo && formatos.countByAtivoTrue() <= 1)
            throw new RegraNegocioException("Deixe ao menos um formato ativo: sem ele não há o que registrar.");
        String antes = retrato(formato);
        formato.editar(nome, porLinha, ativo);
        formatos.flush();
        apoio.auditar("formato", formato.getId(), admin, "EDICAO", antes, retrato(formato), null, null);
        return formato;
    }
    public Formato formato(UUID id) { return formatos.findById(id).orElseThrow(() -> new RegraNegocioException("Formato não encontrado.")); }

    /** RN13: the kiln size; the useful capacity is two lines less (EnfornaService.capacidade). */
    @Transactional
    public Unidade alterarLinhasForno(UsuarioAutenticado p, UUID id, Integer linhas) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        var unidade = unidades.findById(id).orElseThrow(() -> new RegraNegocioException("Unidade não encontrada."));
        if (linhas == null || linhas < 3 || linhas > LINHAS_MAXIMAS)
            throw new RegraNegocioException("Informe as linhas do forno: de 3 a " + LINHAS_MAXIMAS + ".");
        String antes = unidade.getNome() + " · " + unidade.getLinhasForno() + " linhas";
        unidade.alterarLinhasForno(linhas);
        unidades.flush();
        apoio.auditar("unidade", unidade.getId(), admin, "EDICAO", antes, unidade.getNome() + " · " + linhas + " linhas", null, null);
        return unidade;
    }

    @Transactional
    public Motivo criarMotivo(UsuarioAutenticado p, MotivoForm f) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        if (f.tipo() == null) throw new RegraNegocioException("Escolha se o motivo é de correção ou de cancelamento.");
        var descricao = f.descricao() == null ? "" : f.descricao().strip();
        if (descricao.isEmpty() || descricao.length() > 120) throw new RegraNegocioException("Descreva o motivo em até 120 caracteres.");
        apoio.travar("motivo-descricao");
        if (motivos.existsByTipoAndDescricaoIgnoreCase(f.tipo(), descricao)) throw new RegraNegocioException("Este motivo já está na lista.");
        var m = motivos.saveAndFlush(new Motivo(UUID.randomUUID(), f.tipo(), descricao, Boolean.TRUE.equals(f.exigeTexto())));
        apoio.auditar("motivo", m.getId(), admin, "CRIACAO", null, retrato(m), null, null);
        return m;
    }
    /** Reasons are never renamed or deleted, because old corrections point to them; they only leave the list. */
    @Transactional
    public Motivo alternarMotivo(UsuarioAutenticado p, UUID id) {
        var admin = apoio.comPerfil(p, Perfil.ADMINISTRADOR);
        var m = motivos.findById(id).orElseThrow(() -> new RegraNegocioException("Motivo não encontrado."));
        if (m.isAtivo() && motivos.countByTipoAndAtivoTrue(m.getTipo()) <= 1)
            throw new RegraNegocioException("Deixe ao menos um motivo ativo em cada lista.");
        String antes = retrato(m);
        m.alternarAtivo();
        motivos.flush();
        apoio.auditar("motivo", m.getId(), admin, "EDICAO", antes, retrato(m), null, null);
        return m;
    }

    private static String nome(String nome) {
        var n = nome == null ? "" : nome.strip();
        if (n.isEmpty() || n.length() > 120) throw new RegraNegocioException("Informe o nome em até 120 caracteres.");
        return n;
    }
    private String email(String email, UUID proprio) {
        var e = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (e.length() > 180 || !e.matches("[^@\\s]+@[^@\\s]+")) throw new RegraNegocioException("Informe um e-mail válido, como nome@exemplo.com.");
        usuarios.findByEmailIgnoreCase(e).filter(o -> !o.getId().equals(proprio)).ifPresent(o -> {
            throw new RegraNegocioException("Já existe uma conta com este e-mail.");
        });
        return e;
    }
    private static void conferirSenha(String senha) {
        if (senha == null || senha.length() < SENHA_MINIMA) throw new RegraNegocioException("A senha precisa ter ao menos " + SENHA_MINIMA + " caracteres.");
        if (senha.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SENHA_MAXIMA) throw new RegraNegocioException("A senha pode ter até " + SENHA_MAXIMA + " caracteres.");
    }
    /** Only the Registro de Produção belongs to a unit; with a single unit it does not need to be chosen. */
    private Unidade unidadeDoPerfil(Perfil perfil, UUID idUnidade) {
        if (perfil != Perfil.REGISTRO_PRODUCAO) return null;
        var ativas = unidades.findAllByOrderByNome().stream().filter(Unidade::isAtiva).toList();
        if (idUnidade == null && ativas.size() == 1) return ativas.getFirst();
        return ativas.stream().filter(u -> u.getId().equals(idUnidade)).findFirst()
            .orElseThrow(() -> new RegraNegocioException("Escolha a unidade do Registro de Produção."));
    }
    private String nomeFormato(String nome, UUID proprio) {
        var n = nome == null ? "" : nome.strip();
        if (n.isEmpty() || n.length() > 60) throw new RegraNegocioException("Informe o nome do formato em até 60 caracteres.");
        formatos.findByNomeIgnoreCase(n).filter(o -> !o.getId().equals(proprio)).ifPresent(o -> {
            throw new RegraNegocioException("Já existe um formato com este nome.");
        });
        return n;
    }
    private static int tijolosPorLinha(Integer valor) {
        if (valor == null || valor <= 0) throw new RegraNegocioException("Informe quantos tijolos cabem em uma linha.");
        return valor;
    }
    private static String retrato(Usuario u) {
        return u.getNome() + " · " + u.getEmail() + " · " + u.getPerfil().getDescricao() + (u.getUnidade() == null ? "" : " · " + u.getUnidade().getNome())
            + " · " + (u.isAtivo() ? "Ativo" : "Inativo");
    }
    private static String retrato(Formato f) { return f.getNome() + " · " + f.getTijolosPorLinha() + " tijolos por linha · " + (f.isAtivo() ? "Ativo" : "Inativo"); }
    private static String retrato(Motivo m) { return m.getTipo() + " · " + m.getDescricao() + (m.isExigeTexto() ? " · pede descrição" : "") + " · " + (m.isAtivo() ? "Ativo" : "Inativo"); }
}
