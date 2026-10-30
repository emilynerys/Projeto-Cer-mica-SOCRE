package br.com.socre.producao.acesso;

public enum Perfil {
    REGISTRO_PRODUCAO("Registro de Produção"), GESTOR_PRODUCAO("Gestor de Produção"),
    GESTOR_GERAL("Gestor Geral"), ADMINISTRADOR("Administrador");
    private final String descricao;
    Perfil(String descricao) { this.descricao = descricao; }
    public String getDescricao() { return descricao; }
}
