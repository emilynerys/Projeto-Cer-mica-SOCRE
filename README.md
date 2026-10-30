# SOCRE · Controle de produção

Aplicação web desenvolvida para um TCC, destinada ao registro, à conferência e ao acompanhamento da produção de cerâmica vermelha. Este repositório contém a **primeira entrega funcional do SOCRE, centrada em Maromba**, etapa inicial da produção. Os [diagramas UML](docs/uml/README.md) descrevem o modelo previsto para a evolução do sistema; algumas funcionalidades desses diagramas ainda não foram implementadas. O [modelo lógico do banco](docs/modelo-banco-socre.html) mostra as tabelas criadas pelas migrations.

## O que já funciona

- Login e logout, quatro perfis, bloqueio de contas/unidades inativas e permissões verificadas no servidor.
- O perfil Registro de Produção (antes chamado Supervisor) registra Maromba de sua unidade em tijolos, por formato ativo (hoje 6 e 8 furos), confere antes de salvar e consulta somente os próprios registros. Cada formato com produção vira um registro próprio.
- Correção de lançamentos pendentes, conferência e validação pelo Gestor de Produção.
- Histórico com autor, horário e valores antes/depois; gravação e auditoria na mesma transação.
- Proteção contra duplicidade diária por formato, reenvio da mesma requisição e alterações simultâneas.
- Indicadores básicos de Maromba por período/unidade, em milheiros, considerando somente lançamentos validados.
- Aviso de dias sem registro ao selecionar uma unidade. Ausência de registro não vira produção zero.
- Formulários responsivos e rascunho na mesma aba do navegador em caso de falha de conexão.
- Sessões no PostgreSQL: Registro de Produção com 30 dias de inatividade; demais perfis com limite absoluto de 7 dias.

Administração possui apenas consulta dos usuários/unidades de demonstração nesta entrega. Cadastro e redefinição de senha, fornadas, Enforna, Desenforna, correção após validação, relatórios e publicação estão pendentes. O ambiente atual é local e não está pronto para dados reais.

## Tecnologias e requisitos locais

Java com alvo de compilação **21**, Spring Boot **4.1.1**, Spring MVC, Thymeleaf, CSS e JavaScript; Spring Security, Spring Session JDBC, JPA, PostgreSQL e Flyway. Maven **3.9.16** pelo Wrapper, sem precisar instalar Maven globalmente.

Requisitos: Windows, PowerShell, **JDK 21 ou superior** (`java` e `javac` no PATH) e **PostgreSQL 18** com as ferramentas `initdb`, `pg_ctl` e `psql`. O ambiente verificado usa Microsoft OpenJDK **25.0.3** e PostgreSQL **18.4**. Os scripts esperam os binários em `C:\Program Files\PostgreSQL\18\bin`. Nenhum serviço de PostgreSQL instalado é iniciado ou reconfigurado: os scripts criam um cluster próprio dentro de `.local`, escutando apenas em `127.0.0.1:55432`.

## Executar no Windows

Abra um terminal na pasta do projeto, com `java` e `javac` disponíveis no PATH:

```powershell
# Conferir o JDK antes da primeira execução:
java -version
javac -version

# Preparar o banco, compilar e iniciar:
.\scripts\iniciar.ps1
```

Na primeira execução, o script prepara os bancos, gera senhas aleatórias, baixa dependências pelo Maven, compila e inicia a aplicação em segundo plano. É preciso internet para o primeiro download. Abra **http://127.0.0.1:8080**. As credenciais ficam no arquivo local **`.local/ACESSO_LOCAL.md`**.

| Perfil de demonstração | E-mail |
| --- | --- |
| Registro de Produção | registro@socre.local |
| Gestor de Produção | gestor@socre.local |
| Gestor Geral | geral@socre.local |
| Administrador | admin@socre.local |

As contas são geradas somente no perfil `dev`, em banco inicialmente sem usuários. A senha local é compartilhada apenas por essas contas fictícias. A pasta `.local` não entra no Git e não deve ser enviada junto com o código.

Para outro caminho dos binários do PostgreSQL 18, execute primeiro:

```powershell
.\scripts\preparar-local.ps1 -PostgresBin 'D:\PostgreSQL\18\bin'
```

Substitua o caminho pelo da sua instalação. Ele fica salvo na configuração local para os próximos comandos. Os scripts são destinados ao Windows; no Linux/macOS, configure as variáveis de ambiente e as roles do PostgreSQL antes de usar `./mvnw`.

```powershell
# Encerrar a aplicação, preservando os dados e o banco em execução:
.\scripts\parar.ps1

# Recompilar e iniciar após alterações:
.\scripts\iniciar.ps1

# Iniciar o JAR já compilado:
.\scripts\iniciar.ps1 -SemCompilar

# Encerrar também o banco isolado do projeto:
.\scripts\parar.ps1 -Banco
```

Logs: `.local/app.log`, `.local/app-error.log` e `.local/postgres.log`. Não iniciar duas cópias na mesma porta. Se houver outro serviço na 8080, defina `$env:PORT = '8081'` antes de iniciar e use essa porta no navegador.

## Testar

```powershell
.\scripts\testar.ps1
```

Executa JUnit, MockMvc e testes de integração com **PostgreSQL real em `socre_test`**, separado de `socre_dev`. As migrations são aplicadas por Flyway. Cada teste cria suas próprias unidades e contas fictícias; os dados de teste ficam apenas nesse banco. Não apontar esse comando para uma base real.

Os cenários verificam permissões, login e sessões persistentes, expiração e inativação, unicidade sob concorrência, repetição segura do envio, correção com controle de versão, validação, exclusão de pendentes dos totais, preservação da auditoria e rollback quando o histórico falha. Relatórios ficam em `target/surefire-reports`.

A suíte contém **18 testes**: 15 de integração e 3 da política de sessão. A senha fixa no código dos testes é fictícia e exclusiva das contas criadas em `socre_test`; as contas de demonstração recebem outra senha, gerada localmente.

Para gerar o JAR e também executar os testes, use `scripts/testar.ps1`, que executa o ciclo Maven `verify`. Para compilar sem testar:

```powershell
.\mvnw.cmd -B -DskipTests package
```

O artefato fica em `target/producao-0.1.0-SNAPSHOT.jar` e não entra no Git.

Para depurar pela IDE, importe `pom.xml` como Maven. Para executar em primeiro plano pelo terminal, pare a cópia em segundo plano, se houver, e use:

```powershell
.\scripts\preparar-local.ps1
. .\scripts\ambiente-local.ps1
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'
```

Na configuração da IDE, informe as mesmas variáveis listadas abaixo e ative `dev`; não copie senhas para arquivos versionados.

## Configuração e banco

| Variável | Uso |
| --- | --- |
| DB_URL | URL JDBC do banco da aplicação |
| DB_USER / DB_PASSWORD | Credencial restrita de execução; role `socre_app` |
| DB_MIGRATION_USER / DB_MIGRATION_PASSWORD | Credencial proprietária das tabelas, utilizada pelo Flyway |
| SOCRE_DEMO_PASSWORD | Senha dos usuários fictícios; necessária apenas no perfil `dev` |
| SECURE_COOKIE | `true` por padrão; scripts locais usam `false` para HTTP local |
| SERVER_ADDRESS / PORT | Endereço e porta; padrão `127.0.0.1:8080` |

`.env.example` lista as variáveis sem segredos. Spring não carrega esse arquivo automaticamente; use o ambiente do terminal, IDE ou provedor. Os scripts PowerShell carregam `.local/local-secrets.json` apenas no processo atual.

A conta de execução não pode alterar/excluir a auditoria nem criar tabelas. A conta de migrations é diferente. Alterações de estrutura devem gerar novas migrations em `src/main/resources/db/migration`; não editar migrations já aplicadas. Desde a V4, cada etapa tem tabela própria (`maromba`, `enforna`, `desenforna`) e cada registro é um formato em um dia; a V4 também criou `formato`, `motivo`, `fornada` e `inventario_inicial` e moveu a Maromba antiga para o novo modelo, preservando a auditoria. Por enquanto só a Maromba tem telas.

## Organização

```text
src/main/java/br/com/socre/producao/
  acesso/       autenticação, usuários, perfis e sessões
  cadastro/     unidades; próximos cadastros entram aqui
  producao/     registros, validação, consultas e regras
  auditoria/    histórico de produção
  web/          controladores das páginas e apresentação
  config/       dados fictícios restritos ao desenvolvimento
src/main/resources/
  db/migration/ evolução versionada do banco
  templates/    telas Thymeleaf
  static/       CSS, JavaScript e ícone
src/test/       testes automatizados
scripts/        preparação, execução, testes e parada locais
docs/uml/       modelos PlantUML e prévias SVG
```

## Arquivos locais e segurança do commit

O `.gitignore` exclui `.local/` (senhas, acesso, banco e evidências), `.analysis/` (extrações de fontes reais), `target/`, logs, configurações de IDE, chaves privadas, planilhas, exportações e backups. Os documentos internos `docs/PLANEJAMENTO_MVP.md`, `docs/BACKLOG_MVP.md` e `docs/ANDAMENTO.md` também ficam somente na máquina, pois contêm contexto da equipe e análise de dados reais. Eles não são necessários para compilar, executar ou testar.

Versione o código, migrations, testes, scripts, Maven Wrapper, `.env.example` sem senhas, README e diagramas UML. Os scripts geram as credenciais em cada ambiente. Não use `git add -f` para incluir os arquivos protegidos; o `.gitignore` não remove arquivos que já tenham sido adicionados ao Git.

Antes de futuros commits, confira o conteúdo que será enviado:

```powershell
git status --short
git diff --cached --stat
git diff --cached
```

## Demonstrar o primeiro fluxo

1. Entre como Registro de Produção e abra **Registrar Maromba**.
2. Informe 12.000 tijolos de 6 furos e 3.000 de 8 furos em uma data sem registro. Confira e salve.
3. Devem aparecer dois registros **Pendentes**, um por formato. Abra o de 6 furos, corrija para 13.000 e veja o histórico.
4. Saia e entre como Gestor de Produção. Abra cada registro, confira e valide.
5. Abra Indicadores: o total deve ser **16,0 milheiros de Maromba**. Esses valores não representam produção acabada.
6. Entre como Gestor Geral para consultar. O Registro de Produção já não pode editar esse registro validado.

O rascunho sobrevive a recarregamentos na mesma aba. Sair da conta o remove; fechar a aba normalmente encerra esse armazenamento. Não há fila offline nem envio automático posterior. O botão **Descartar rascunho** permite carregar a versão atual do servidor após conflito de edição.

## Limitações e evolução

Próximos passos: cadastros administrativos, ciclo de fornadas, etapas Enforna/Desenforna, relatórios e correções após validação. As regras operacionais desses fluxos devem ser confirmadas antes da implementação.

Publicação ainda não foi realizada. Antes de usar dados reais, completar o núcleo, provisionar contas individuais, preparar HTTPS, backup/restauração, suporte e validação com os usuários. O perfil `dev` e os bancos locais servem exclusivamente ao desenvolvimento.

## Problemas comuns

- **PostgreSQL não encontrado:** execute `preparar-local.ps1 -PostgresBin` com o diretório correto de binários.
- **`java` ou `javac` não encontrado:** instale um JDK e configure o PATH; um JRE sozinho não compila o projeto.
- **Porta 8080 ocupada:** defina `$env:PORT = '8081'` antes de iniciar e abra `http://127.0.0.1:8081`.
- **Falha ao iniciar o banco:** consulte `.local/postgres.log`; o cluster do projeto precisa da porta 55432 livre. Não exclua `.local/postgres-data` para resolver falhas, pois contém os dados locais.
- **Falha ao baixar dependências:** confira a conexão e o acesso ao Maven Central. A primeira execução precisa baixar o Maven e as dependências.
- **PowerShell bloqueia o script:** em um terminal autorizado para desenvolvimento, use `Set-ExecutionPolicy -Scope Process -ExecutionPolicy RemoteSigned` e repita o comando. A alteração vale apenas para aquele terminal.
