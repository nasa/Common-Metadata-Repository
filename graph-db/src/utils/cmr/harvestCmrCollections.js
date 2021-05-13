const { fetchPageFromCMR } = require('./fetchPageFromCmr')
const { clearScrollSession } = require('./clearScrollSession')

/**
 * Harvest UMM JSON collection metadata from CMR environment set in env variable
 * @returns {JSON} partitioned array of CMR collection search results
 */
exports.harvestCmrCollections = async () => {
  const response = await fetchPageFromCMR()
  const results = (await response.json()).items
  const scrollId = response.headers.get('CMR-Scroll-Id')
  const partitionedSearchResults = []
  let continueScroll = true

  partitionedSearchResults.push(results)
  while (continueScroll) {
    // eslint-disable-next-line no-await-in-loop
    const scrolledResults = await fetchPageFromCMR(scrollId)
      .then((scrollResponse) => scrollResponse.json())
      .then((json) => {
        if (json.errors) {
          throw new Error(`The following errors ocurred: ${json.errors}`)
        } else {
          return json.items
        }
      })
      .catch((error) => {
        console.warn(`Could not complete request due to error: ${error}`)
        return null
      })

    partitionedSearchResults.push(scrolledResults)

    if (!scrolledResults || scrolledResults.length < process.env.PAGE_SIZE) {
      continueScroll = false
    }
  }

  console.log(`Got scroll-id: [${scrollId}]. Clearing session...`)
  await clearScrollSession(scrollId)

  return partitionedSearchResults
}
