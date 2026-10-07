package br.gov.compras.rpa.db;

import br.gov.compras.rpa.model.MensagemChat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class DatabaseService {

    private static final String URL = "jdbc:h2:./chat-db";
    private static final String USER = "sa";
    private static final String PASS = "";

    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASS);
    }

    public static void criarTabela() throws Exception {

        Connection conn = getConnection();

        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS CHAT_MENSAGENS (
                ID VARCHAR(200) PRIMARY KEY,
                TEXTO CLOB,
                REMETENTE VARCHAR(100),
                DATA VARCHAR(50),
                ITEM_LOTE INT
            )
        """);

        conn.close();
    }

    public static void salvar(MensagemChat m) {

        try (Connection conn = getConnection()) {

            PreparedStatement ps = conn.prepareStatement("""
                MERGE INTO CHAT_MENSAGENS (ID, TEXTO, REMETENTE, DATA, ITEM_LOTE)
                KEY(ID)
                VALUES (?, ?, ?, ?, ?)
            """);

            ps.setString(1, m.id);
            ps.setString(2, m.texto);
            ps.setString(3, m.remetente);
            ps.setString(4, m.data);
            ps.setObject(5, m.itemLote);

            ps.execute();

            System.out.println("💾 Salvo: " + m.texto);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}