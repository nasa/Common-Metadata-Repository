/**
 * Returns an environment specific configuration object for SQS
 * @return {Object} A configuration object for SQS
 */
export const getSqsConfig = () => {
  const productionConfig = {
    apiVersion: '2012-11-05'
  }

  const { env } = process
  const {
    IS_LOCAL: isLocal,
    IS_OFFLINE: isOffline,
    NODE_ENV: nodeEnv
  } = env

  if (isLocal || isOffline || nodeEnv === 'test') {
    // The endpoint should point to the serverless offline host:port
    return {
      ...productionConfig,
      region: 'us-east-1',
      endpoint: 'http://localhost:9324'
    }
  }

  return productionConfig
}
