import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.List;
import java.util.StringJoiner;

public class MigrateH2ToMysql {
    private static final List<String> TABLES = List.of(
        "users",
        "targets",
        "scans",
        "findings",
        "notifications",
        "scan_schedules"
    );

    public static void main(String[] args) throws Exception {
        String h2Url = args.length > 0
            ? args[0]
            : "jdbc:h2:file:/home/kali/Project/web_scanner_anti/backend/data/scannerx;MODE=MySQL;AUTO_SERVER=TRUE;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=READ,VALUE,USER";
        String h2User = args.length > 1 ? args[1] : "sa";
        String h2Password = args.length > 2 ? args[2] : "";

        String mysqlUrl = args.length > 3
            ? args[3]
            : "jdbc:mysql://127.0.0.1:3306/scannerx?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        String mysqlUser = args.length > 4 ? args[4] : "scanner";
        String mysqlPassword = args.length > 5 ? args[5] : "scanner_pass";

        try (
            Connection h2 = DriverManager.getConnection(h2Url, h2User, h2Password);
            Connection mysql = DriverManager.getConnection(mysqlUrl, mysqlUser, mysqlPassword)
        ) {
            mysql.setAutoCommit(false);

            try (Statement statement = mysql.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
            }

            try {
                for (String table : TABLES) {
                    migrateTable(h2, mysql, table);
                }

                try (Statement statement = mysql.createStatement()) {
                    statement.execute("SET FOREIGN_KEY_CHECKS=1");
                }

                mysql.commit();
            } catch (Exception ex) {
                mysql.rollback();
                throw ex;
            }
        }
    }

    private static void migrateTable(Connection h2, Connection mysql, String table) throws Exception {
        int sourceCount = countRows(h2, table);

        try (Statement deleteStatement = mysql.createStatement()) {
            deleteStatement.executeUpdate("DELETE FROM " + table);
        }

        if (sourceCount == 0) {
            System.out.println(table + ": 0 rows");
            return;
        }

        try (
            Statement readStatement = h2.createStatement();
            ResultSet rs = readStatement.executeQuery("SELECT * FROM " + table)
        ) {
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            StringJoiner columnJoiner = new StringJoiner(", ");
            StringJoiner placeholderJoiner = new StringJoiner(", ");

            for (int i = 1; i <= columnCount; i++) {
                columnJoiner.add(metaData.getColumnName(i));
                placeholderJoiner.add("?");
            }

            String sql = "INSERT INTO " + table + " (" + columnJoiner + ") VALUES (" + placeholderJoiner + ")";

            try (PreparedStatement insert = mysql.prepareStatement(sql)) {
                int migrated = 0;
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        insert.setObject(i, rs.getObject(i));
                    }
                    insert.addBatch();
                    migrated++;
                }
                insert.executeBatch();
                System.out.println(table + ": " + migrated + " rows");
            }
        }
    }

    private static int countRows(Connection connection, String table) throws Exception {
        try (
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)
        ) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
