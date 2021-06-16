import { clearScrollSession } from '../utils/cmr/clearScrollSession'
import { fetchPageFromCMR } from '../utils/cmr/fetchPageFromCMR'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlineConnection
let token

const bootstrapGremlinServer = async () => {
  // Prevent creating more tokens than necessary
  if (!token) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlineConnection) {
    gremlineConnection = initializeGremlinConnection()
  }

  // Fetch all CMR Collections and index each page, utlimately returning the scroll session
  // id if once was created
  const scrollId = await fetchPageFromCMR(null, token, gremlineConnection)

  // If a scroll session was created we need to inform CMR that we are done with it
  if (scrollId) {
    await clearScrollSession(scrollId)
  }

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: 'Indexing completed'
  }
}

export default bootstrapGremlinServer
