import axios from 'axios'

/**
 * Given a CMR Scroll Session ID, clear that session from memory, freeing up more CMR resources
 * @param {String} scrollId
 * @returns Status code response
 */
export const clearScrollSession = async (scrollId) => {
  // Take no action if no scroll id was provided
  if (!scrollId) return null

  console.log(`Clearing scroll session with '${scrollId}'...`)

  let response

  try {
    response = await axios({
      method: 'post',
      url: `${process.env.CMR_ROOT}/search/clear-scroll`,
      data: JSON.stringify({ scroll_id: scrollId }),
      headers: {
        'Content-Type': 'application/json'
      }
    })

    console.log(`Cleared scroll session with id '${scrollId}'`)
  } catch (error) {
    console.log(`Could not clear scroll session [${scrollId}] due to error: ${error}`)

    return null
  }

  return response
}
