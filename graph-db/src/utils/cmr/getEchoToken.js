const { getSecureParam } = require('../getSecureParam');

/**
 * getEchoToken: Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
 exports.getEchoToken = async () => {
  const IS_LOCAL = process.env.IS_LOCAL;
  
  if (IS_LOCAL) {
    return null;
  }
  
  const response = getSecureParam(
    `/${process.env.CMR_ENVIRONMENT}/graph-db/CMR_ECHO_SYSTEM_TOKEN`
  );

  if (!IS_LOCAL && !response) {
    throw new Error('ECHO Token not found. Please update config!');
  }

  console.log('Retrieved ECHO TOKEN');
  return response;
};