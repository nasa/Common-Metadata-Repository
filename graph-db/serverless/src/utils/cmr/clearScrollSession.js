import fetch from 'node-fetch'

/**
 * Given a CMR Scroll Session ID, clear that session from memory, freeing up more CMR resources
 * @param {String} scrollId
 * @returns Status code response
 */
export const clearScrollSession = async (scrollId) => {
  if (!scrollId) return null

  console.log(`Clearing scroll session with '${scrollId}'...`)

  const response = await fetch(`${process.env.CMR_ROOT}/search/clear-scroll`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ scroll_id: scrollId })
  })
    .then((res) => res.status)
    .catch((error) => {
      console.warn(`Could not clear scroll session [${scrollId}] due to error: ${error}`)
      return null
    })

  console.log(`Cleared scroll session with id '${scrollId}'`)

  return response
}
