import AWS from 'aws-sdk'
import { clearScrollSession } from '../utils/cmr/clearScrollSession'
import { fetchPageFromCMR } from '../utils/cmr/fetchPageFromCMR'
import { getEchoToken } from '../utils/cmr/getEchoToken'
import { closeGremlinConnection, initializeGremlinConnection } from '../utils/gremlin/initializeGremlinConnection'

let gremlinConnection
let token
let sqs

const bootstrapGremlinServer = async (event) => {
  // Prevent creating more tokens than necessary
  if (!token) {
    token = await getEchoToken()
  }

  // Prevent connecting to Gremlin more than necessary
  if (!gremlinConnection) {
    gremlinConnection = initializeGremlinConnection()
  }

  if (sqs == null) {
    sqs = new AWS.SQS({ apiVersion: '2012-11-05' })
  }

  const { Records: bootstrapEvents = ['{}'] } = event

  const { body } = bootstrapEvents[0]

  const { 'provider-id': providerId = null } = JSON.parse(body)

  // Fetch all CMR Collections and index each page, utlimately returning the scroll session
  // id if once was created
  const scrollId = await fetchPageFromCMR({
    scrollId: null,
    token,
    gremlinConnection,
    providerId,
    sqs
  })

  // If a scroll session was created we need to inform CMR that we are done with it
  if (scrollId) {
    await clearScrollSession(scrollId)
  }

  // CLOSE_CONNECTION allows serverless bootstrap call to exit successfully.
  const { env: { CLOSE_CONNECTION } } = process

  if (CLOSE_CONNECTION === 'true') {
    closeGremlinConnection()
  }

  console.log('Bootstrap completed.')

  return {
    isBase64Encoded: false,
    statusCode: 200,
    body: 'Indexing completed'
  }
}

export default bootstrapGremlinServer
