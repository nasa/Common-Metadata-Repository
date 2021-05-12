const { process: p, driver } = require('gremlin')

/**
 * Connects to gremlin server to give reusable connection
 * @returns Gremlin traversal interface
 */
exports.initializeGremlinConnection = () => {
  const { traversal } = p.AnonymousTraversalSource
  const { DriverRemoteConnection } = driver
  const gremlinUrl = process.env.GREMLIN_URL

  return traversal().withRemote(new DriverRemoteConnection(gremlinUrl))
}
