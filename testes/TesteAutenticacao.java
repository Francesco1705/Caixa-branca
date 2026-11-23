import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

public class TesteAutenticacao {
    private static int aprovados = 0;
    private static int falhas = 0;

    public static void main(String[] args) throws Exception {
        executar("login válido", TesteAutenticacao::loginValido);
        executar("usuário não encontrado", TesteAutenticacao::usuarioNaoEncontrado);
        executar("entrada vazia não abre conexão", TesteAutenticacao::entradaVazia);
        executar("falha de conexão é propagada", TesteAutenticacao::falhaConexao);
        executar("estado anterior é resetado", TesteAutenticacao::estadoResetado);
        executar("falha SQL é propagada", TesteAutenticacao::falhaSql);
        System.out.println("\nResumo: " + aprovados + " passaram; " + falhas + " falharam.");
        if (falhas > 0) System.exit(1);
    }

    private static void executar(String nome, Caso caso) {
        try { caso.executar(); aprovados++; System.out.println("[PASSOU] " + nome); }
        catch (Throwable e) { falhas++; System.out.println("[FALHOU] " + nome + " - " + e); }
    }

    private static void loginValido() throws Exception {
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() -> conexaoComResultado(true, "Francesco", false));
        exigir(auth.autenticar("francesco", "senha"), "deveria autenticar");
        exigir("Francesco".equals(auth.getUltimoUsuario()), "nome não foi atualizado");
    }

    private static void usuarioNaoEncontrado() throws Exception {
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() -> conexaoComResultado(false, null, false));
        exigir(!auth.autenticar("inexistente", "senha"), "não deveria autenticar");
    }

    private static void entradaVazia() throws Exception {
        AtomicInteger chamadas = new AtomicInteger();
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() -> { chamadas.incrementAndGet(); return null; });
        exigir(!auth.autenticar(" ", "senha"), "entrada vazia deveria ser recusada");
        exigir(chamadas.get() == 0, "não deveria abrir conexão");
    }

    private static void falhaConexao() throws Exception {
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() -> { throw new SQLException("falha simulada"); });
        esperarSQLException(() -> auth.autenticar("user", "pass"));
    }

    private static void estadoResetado() throws Exception {
        AtomicInteger tentativa = new AtomicInteger();
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() ->
                tentativa.getAndIncrement() == 0 ? conexaoComResultado(true, "Francesco", false)
                                                 : conexaoComResultado(false, null, false));
        exigir(auth.autenticar("user", "pass"), "primeira tentativa deveria autenticar");
        exigir(!auth.autenticar("user", "errada"), "segunda tentativa deveria falhar");
        exigir(auth.getUltimoUsuario().isEmpty(), "nome antigo não deveria permanecer");
    }

    private static void falhaSql() throws Exception {
        AutenticacaoRevisada auth = new AutenticacaoRevisada(() -> conexaoComResultado(false, null, true));
        esperarSQLException(() -> auth.autenticar("user", "pass"));
    }

    private static Connection conexaoComResultado(boolean encontrou, String nome, boolean falharConsulta) {
        ResultSet rs = proxy(ResultSet.class, (obj, method, args) -> {
            switch (method.getName()) {
                case "next": if (falharConsulta) throw new SQLException("erro em next"); return encontrou;
                case "getString": if (falharConsulta) throw new SQLException("erro em getString"); return nome;
                case "close": return null;
                default: return padrao(method.getReturnType());
            }
        });
        PreparedStatement ps = proxy(PreparedStatement.class, (obj, method, args) -> {
            switch (method.getName()) {
                case "executeQuery": if (falharConsulta) throw new SQLException("erro na consulta"); return rs;
                case "setString": case "close": return null;
                default: return padrao(method.getReturnType());
            }
        });
        return proxy(Connection.class, (obj, method, args) -> {
            switch (method.getName()) {
                case "prepareStatement": return ps;
                case "close": return null;
                default: return padrao(method.getReturnType());
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> tipo, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, handler);
    }

    private static Object padrao(Class<?> tipo) {
        if (!tipo.isPrimitive()) return null;
        if (tipo == boolean.class) return false;
        if (tipo == int.class) return 0;
        if (tipo == long.class) return 0L;
        if (tipo == double.class) return 0d;
        if (tipo == float.class) return 0f;
        if (tipo == short.class) return (short) 0;
        if (tipo == byte.class) return (byte) 0;
        if (tipo == char.class) return '\0';
        return null;
    }

    private static void exigir(boolean condicao, String mensagem) {
        if (!condicao) throw new AssertionError(mensagem);
    }

    private static void esperarSQLException(Caso caso) throws Exception {
        try { caso.executar(); throw new AssertionError("era esperada uma SQLException"); }
        catch (SQLException esperado) { /* resultado esperado */ }
    }

    @FunctionalInterface private interface Caso { void executar() throws Exception; }
}
