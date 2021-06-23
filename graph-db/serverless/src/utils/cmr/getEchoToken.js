import { getSecureParam } from './getSecureParam'

/**
 * Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything if no token is supplied.
 */
export const getEchoToken = async () => {
  const { env: { IS_LOCAL } } = process

  if (IS_LOCAL === 'true') {
    let localToken = process.env.TOKEN
    if (localToken === undefined) {
      localToken = null
    }

    return localToken
  }

  let token

  try {
    token = await getSecureParam(
      `/${process.env.ENVIRONMENT}/graph-db/${process.env.CMR_TOKEN_KEY}`
    )
  } catch (error) {
    console.error(`Could not get ECHO token: ${error}`)

    token = null
  }

  return token
}
