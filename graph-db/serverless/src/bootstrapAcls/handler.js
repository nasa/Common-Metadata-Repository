import { fetchPageFromAcls } from '../utils/cmr/fetchPageFromAcls'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token

const bootstrapAcls = async () => {
  // Prevent creating more tokens than necessary
  if (token === undefined) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
    gremlinConnection = initializeGremlinConnection()
  }

  // Fetch all CMR Collections and index each page
  await fetchPageFromAcls({
    searchAfter: null,
    token,
    gremlinConnection
  })

  console.log('Bootstrap completed.')

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: 'Indexing completed'
  }
}

export default bootstrapAcls
