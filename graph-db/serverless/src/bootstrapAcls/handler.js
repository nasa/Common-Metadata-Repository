//TODO don't forget that this has to be added to the serverless.yml file
import { fetchCollectionAcls } from '../utils/cmr/fetchCollectionAcls'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token
// TODO what is this event referring to? I guess some file
const bootstrapAcls = async (event) => {
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

  // Fetch all Acls in env and index them into graphDb
  const fetchedAcls = await fetchCollectionAcls(token)
  
  console.log('Bootstrap Acls completed.')

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: 'Indexing completed'
  }
}

export default bootstrapAcls