import gremlin from 'gremlin'

const { DriverRemoteConnection } = gremlin.driver
const { Graph } = gremlin.structure

let connection
let driverRC

export const driverRemoteConnection = () => {
  if (driverRC) {
    return driverRC
  }

  const gremlinUrl = process.env.GREMLIN_URL

  driverRC = new DriverRemoteConnection(gremlinUrl, {})

  return driverRC
}

/**
 * Connects to gremlin server to give reusable connection
 * @returns Gremlin traversal interface
 */
export const initializeGremlinConnection = () => {
  if (connection) {
    return connection
  }

  driverRC = driverRemoteConnection()

  const graph = new Graph()
  connection = graph.traversal().withRemote(driverRC)

  return connection
}
