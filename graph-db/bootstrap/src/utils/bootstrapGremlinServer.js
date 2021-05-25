const { initializeGremlinConnection } = require('./gremlin/initializeGremlinConnection')
const { indexPageOfCmrResults } = require('./indexing/indexPageOfCmrResults')
const { clearScrollSession } = require('./cmr/clearScrollSession')
const { fetchPageFromCMR } = require('./cmr/fetchPageFromCmr')
const { getEchoToken } = require('./cmr/getEchoToken')

/**
 * Harvests collections from CMR and loads them into graph db
 * @returns {Array} Errors from indexing into graph db
 */
exports.bootstrapGremilinServer = async () => {
  const echoToken = await getEchoToken()
  const gremlin = initializeGremlinConnection()

  // Get the first page of collections from the CMR to initiate the scroll
  // session. Parse the JSON results, annd pull the scroll id from the headers
  const response = await fetchPageFromCMR(null, echoToken)
  const results = (await response.json()).items
  const scrollId = response.headers.get('CMR-Scroll-Id')
  let continueScroll = true

  // Index first page of CMR results before scrolling and indexing the rest
  indexPageOfCmrResults(results, gremlin)

  while (continueScroll) {
    // eslint-disable-next-line no-await-in-loop
    const scrolledResults = await fetchPageFromCMR(scrollId, echoToken)
      .then((scrollResponse) => scrollResponse.json())
      .then((json) => {
        if (json.errors) {
          throw new Error(`The following errors ocurred: ${json.errors}`)
        }

        return json.items
      })
      .catch((error) => {
        console.warn(`Could not complete request due to error: ${error}`)
        return null
      })

    // The first two cases are likely the result of CMR errors
    if (!scrollId || !scrolledResults || scrolledResults.length < process.env.PAGE_SIZE) {
      continueScroll = false
    }

    // eslint-disable-next-line no-await-in-loop
    await indexPageOfCmrResults(scrolledResults, gremlin)
  }

  console.log(`Got scroll-id: [${scrollId}]. Clearing session...`)
  await clearScrollSession(scrollId)
}
