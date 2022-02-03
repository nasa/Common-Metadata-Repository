import gremlin from 'gremlin'

const { DriverRemoteConnection } = gremlin.driver
const { Graph } = gremlin.structure

let connection
let driverRC

export const closeGremlinConnection = () => {
  if (driverRC) {
    driverRC.close()
    driverRC = null
    connection = null
  }
}

/**
 * Connects to gremlin server to give reusable connection
 * @returns Gremlin traversal interface
 */
export const initializeGremlinConnection = () => {
  if (connection) {
    return connection
  }

  const gremlinUrl = process.env.GREMLIN_URL

  driverRC = new DriverRemoteConnection(gremlinUrl, {})

  const graph = new Graph()
  connection = graph.traversal().withRemote(driverRC)

  return connection
}
