/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.oracle;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.airbyte.commons.json.Jsons;
import io.airbyte.commons.string.Strings;
import io.airbyte.db.Database;
import io.airbyte.db.Databases;
import io.airbyte.db.jdbc.JdbcDatabase;
import io.airbyte.integrations.destination.ExtendedNameTransformer;
import io.airbyte.integrations.standardtest.destination.DestinationAcceptanceTest;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.jooq.JSONFormat;
import org.jooq.JSONFormat.RecordFormat;
import org.junit.Test;

public class UnencryptedOracleDestinationAcceptanceTest extends DestinationAcceptanceTest {

  private static final JSONFormat JSON_FORMAT = new JSONFormat().recordFormat(RecordFormat.OBJECT);

  private ExtendedNameTransformer namingResolver = new OracleNameTransformer();
  private static OracleContainer db;
  private static JsonNode config;
  private final String schemaName = "TEST_ORCL";

  @Override
  protected String getImageName() {
    return "airbyte/destination-oracle:dev";
  }

  private JsonNode getConfig(OracleContainer db) {

    return Jsons.jsonNode(ImmutableMap.builder()
        .put("host", db.getHost())
        .put("port", db.getFirstMappedPort())
        .put("sid", db.getSid())
        .put("username", db.getUsername())
        .put("password", db.getPassword())
        .put("schemas", List.of("JDBC_SPACE"))
        .put("encryption", Jsons.jsonNode(ImmutableMap.builder()
            .put("encryption_method", "unencrypted")
            .build()))
        .build());
  }

  @Override
  protected JsonNode getConfig() {
    return Jsons.clone(config);
  }

  @Override
  protected List<JsonNode> retrieveRecords(TestDestinationEnv env,
                                           String streamName,
                                           String namespace,
                                           JsonNode streamSchema)
      throws Exception {
    return retrieveRecordsFromTable(namingResolver.getRawTableName(streamName), namespace)
        .stream()
        .map(r -> Jsons.deserialize(
            r.get(OracleDestination.COLUMN_NAME_DATA.replace("\"", "")).asText()))
        .collect(Collectors.toList());
  }

  @Override
  protected boolean implementsNamespaces() {
    return true;
  }

  @Override
  protected boolean supportsDBT() {
    return true;
  }

  @Override
  protected List<JsonNode> retrieveNormalizedRecords(TestDestinationEnv env,
                                                     String streamName,
                                                     String namespace)
      throws Exception {
    String tableName = namingResolver.getIdentifier(streamName);
    return retrieveRecordsFromTable(tableName, namespace);
  }

  @Override
  protected JsonNode getFailCheckConfig() {
    final JsonNode invalidConfig = getConfig();
    ((ObjectNode) invalidConfig).put("password", "wrong password");
    return invalidConfig;
  }

  @Override
  protected List<String> resolveIdentifier(String identifier) {
    final List<String> result = new ArrayList<>();
    final String resolved = namingResolver.getIdentifier(identifier);
    result.add(identifier);
    result.add(resolved);
    if (!resolved.startsWith("\"")) {
      result.add(resolved.toLowerCase());
      result.add(resolved.toUpperCase());
    }
    return result;
  }

  private List<JsonNode> retrieveRecordsFromTable(String tableName, String schemaName)
      throws SQLException {
    List<org.jooq.Record> result = getDatabase(config)
        .query(ctx -> ctx.fetch(
            String.format("SELECT * FROM %s.%s ORDER BY %s ASC", schemaName, tableName,
                OracleDestination.COLUMN_NAME_EMITTED_AT))
            .stream()
            .collect(Collectors.toList()));
    return result
        .stream()
        .map(r -> r.formatJSON(JSON_FORMAT))
        .map(Jsons::deserialize)
        .collect(Collectors.toList());
  }

  private static Database getDatabase(JsonNode config) {
    return Databases.createDatabase(
        config.get("username").asText(),
        config.get("password").asText(),
        String.format("jdbc:oracle:thin:@//%s:%s/%s",
            config.get("host").asText(),
            config.get("port").asText(),
            config.get("sid").asText()),
        "oracle.jdbc.driver.OracleDriver",
        null);
  }

  @Override
  protected void setup(TestDestinationEnv testEnv) throws Exception {
    final String dbName = Strings.addRandomSuffix("db", "_", 10);
    db = new OracleContainer()
        .withUsername("test")
        .withPassword("oracle")
        .usingSid();
    db.start();

    config = getConfig(db);

    final Database database = getDatabase(config);
    database.query(
        ctx -> ctx.fetch(String.format("CREATE USER %s IDENTIFIED BY %s", schemaName, schemaName)));
    database.query(ctx -> ctx.fetch(String.format("GRANT ALL PRIVILEGES TO %s", schemaName)));

    database.close();

    ((ObjectNode) config).put("schema", dbName);
  }

  @Override
  protected void tearDown(TestDestinationEnv testEnv) {
    db.stop();
    db.close();
  }

  @Test
  public void testNoneEncryption() throws SQLException {
    JsonNode config = getConfig();

    JdbcDatabase database = Databases.createJdbcDatabase(config.get("username").asText(),
        config.get("password").asText(),
        String.format("jdbc:oracle:thin:@//%s:%s/%s",
            config.get("host").asText(),
            config.get("port").asText(),
            config.get("sid").asText()),
        "oracle.jdbc.driver.OracleDriver");

    String network_service_banner = "select network_service_banner from v$session_connect_info where sid in (select distinct sid from v$mystat)";
    List<JsonNode> collect = database.query(network_service_banner).collect(Collectors.toList());

    assertTrue(collect.get(1).get("NETWORK_SERVICE_BANNER").asText()
        .contains("Oracle Advanced Security: encryption"));
  }

}
