import { getSecureParam } from './getSecureParam'

/**
 * Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests if it is configured via CMR_TOKEN_KEY.
 * The function will return null when IS_LOCAL is true or CMR_TOKEN_KEY is not configured.
 * In case of error, the function will return undefined.
 * By Default, CMR_TOKEN_KEY should not be configured so that only public collections are indexed in graph db.
 */
export const getEchoToken = async () => {
  const { env = {} } = process
  // Destructure env variables
  const {
    CMR_TOKEN_KEY: cmrTokenKey,
    ENVIRONMENT: envrionment,
    IS_LOCAL: isLocal,
    TOKEN: localToken
  } = env

  if (isLocal === 'true') {
    return localToken
  }

  let token

  if (cmrTokenKey) {
    try {
      token = await getSecureParam(
        `/${envrionment}/graph-db/${cmrTokenKey}`
      )
    } catch (error) {
      console.log(`Could not get token: ${error}`)
    }
  } else {
    token = null
  }

  return token
}
