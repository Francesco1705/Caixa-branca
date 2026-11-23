import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Versão revisada para demonstrar práticas mais seguras com JDBC. */
public class AutenticacaoRevisada {
    @FunctionalInterface
    public interface ConnectionProvider {
        Connection abrir() throws SQLException;
    }

    private static final String SQL =
            "SELECT nome FROM usuarios WHERE usuario = ? AND senha = ?";

    private final ConnectionProvider connectionProvider;
    private String ultimoUsuario = "";
    private boolean autenticado;

    public AutenticacaoRevisada() {
        this(() -> DriverManager.getConnection(
                System.getenv("DB_URL"),
                System.getenv("DB_USER"),
                System.getenv("DB_PASSWORD")));
    }

    public AutenticacaoRevisada(ConnectionProvider connectionProvider) {
        if (connectionProvider == null) {
            throw new IllegalArgumentException("O provedor de conexão é obrigatório.");
        }
        this.connectionProvider = connectionProvider;
    }

    public boolean autenticar(String usuario, String senha) throws SQLException {
        autenticado = false;
        ultimoUsuario = "";

        if (usuario == null || usuario.isBlank() || senha == null || senha.isBlank()) {
            return false;
        }

        try (Connection conexao = connectionProvider.abrir();
             PreparedStatement comando = conexao.prepareStatement(SQL)) {
            comando.setString(1, usuario);
            comando.setString(2, senha);
            try (ResultSet resultado = comando.executeQuery()) {
                if (resultado.next()) {
                    ultimoUsuario = resultado.getString("nome");
                    autenticado = true;
                }
            }
        }
        return autenticado;
    }

    public String getUltimoUsuario() {
        return ultimoUsuario;
    }
}
