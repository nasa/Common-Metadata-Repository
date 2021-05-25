const { process: proc, driver } = require('gremlin')

let connection

/**
 * Connects to gremlin server to give reusable connection
 * @returns Gremlin traversal interface
 */
exports.initializeGremlinConnection = () => {
  if (connection) {
    return connection
  }

  const { traversal } = proc.AnonymousTraversalSource
  const { DriverRemoteConnection } = driver
  const gremlinUrl = process.env.GREMLIN_URL

  connection = traversal().withRemote(new DriverRemoteConnection(gremlinUrl))
  return connection
}
