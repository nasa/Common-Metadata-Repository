const { getSecureParam } = require('./getSecureParam')

/**
 * Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
exports.getEchoToken = async () => {
  const { env: { IS_LOCAL } } = process

  console.log(`IS_LOCAL: ${IS_LOCAL}`)
  if (IS_LOCAL === true) {
    return null
  }

  const response = await getSecureParam(
    `/${process.env.CMR_ENVIRONMENT}/graph-db/CMR_ECHO_SYSTEM_TOKEN`
  )

  if (!response) {
    throw new Error('ECHO Token not found. Please update config!')
  }
  console.log(`RESPONSE: ${response}`)

  return response
}
