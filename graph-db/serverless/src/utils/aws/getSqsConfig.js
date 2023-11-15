/**
 * Returns an environment specific configuration object for Lambda
 * @return {Object} A configuration object for Lambda
 */
export const getSqsConfig = () => {
  const productionConfig = {
    apiVersion: '2012-11-05'
  }

  if (process.env.IS_OFFLINE || process.env.IS_LOCAL) {
    // The endpoint should point to the serverless offline host:port
    return {
      ...productionConfig,
      region: 'us-east-1',
      endpoint: 'http://localhost:9324'
    }
  }

  return productionConfig
}
