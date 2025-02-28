/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.mysql;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airbyte.commons.json.Jsons;
import io.airbyte.integrations.base.Destination;
import io.airbyte.integrations.base.IntegrationRunner;
import io.airbyte.integrations.base.spec_modification.SpecModifyingDestination;
import io.airbyte.protocol.models.ConnectorSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySQLDestinationStrictEncrypt extends SpecModifyingDestination implements Destination {

  private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDestinationStrictEncrypt.class);

  public MySQLDestinationStrictEncrypt() {
    super(MySQLDestination.sshWrappedDestination());
  }

  @Override
  public ConnectorSpecification modifySpec(ConnectorSpecification originalSpec) {
    final ConnectorSpecification spec = Jsons.clone(originalSpec);
    ((ObjectNode) spec.getConnectionSpecification().get("properties")).remove("ssl");
    return spec;
  }

  public static void main(String[] args) throws Exception {
    final Destination destination = new MySQLDestinationStrictEncrypt();
    LOGGER.info("starting destination: {}", MySQLDestinationStrictEncrypt.class);
    new IntegrationRunner(destination).run(args);
    LOGGER.info("completed destination: {}", MySQLDestinationStrictEncrypt.class);
  }

}
