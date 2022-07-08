import { fetchPageFromCMR } from '../utils/cmr/fetchPageFromCMR'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token

const bootstrapGremlinServer = async (event) => {
  // Prevent creating more tokens than necessary
  if (token === undefined) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
    gremlinConnection = initializeGremlinConnection()
  }

  const { Records: bootstrapEvents } = event

  const { body } = bootstrapEvents[0]

  const { 'provider-id': providerId = null } = JSON.parse(body)

  // Fetch all CMR Collections and index each page
  await fetchPageFromCMR({
    searchAfter: null,
    token,
    gremlinConnection,
    providerId
  })

  console.log('Bootstrap completed.')

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: 'Indexing completed'
  }
}

export default bootstrapGremlinServer
