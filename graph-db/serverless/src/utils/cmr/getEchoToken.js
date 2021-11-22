import { getSecureParam } from './getSecureParam'

/**
 * Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests if it is configured via CMR_TOKEN_KEY.
 * The function will return null when IS_LOCAL is true or CMR_TOKEN_KEY is not configured.
 * In case of error, the function will return undefined.
 * By Default, CMR_TOKEN_KEY should not be configured so that only public collections are indexed in graph db.
 */
export const getEchoToken = async () => {
  const { env: { IS_LOCAL, CMR_TOKEN_KEY } } = process

  if (IS_LOCAL === 'true') {
    let localToken = process.env.TOKEN
    if (localToken === undefined) {
      localToken = null
    }

    return localToken
  }

  let token

  if (CMR_TOKEN_KEY) {
    try {
      token = await getSecureParam(
        `/${process.env.ENVIRONMENT}/graph-db/${process.env.CMR_TOKEN_KEY}`
      )
    } catch (error) {
      console.log(`Could not get ECHO token: ${error}`)
    }
  } else {
    token = null
  }

  return token
}
