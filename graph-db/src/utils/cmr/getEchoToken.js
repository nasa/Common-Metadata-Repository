const { getSecureParam } = require('../getSecureParam');

/**
 * getEchoToken: Fetch token for CMR requests
 * @returns {String} ECHO Token for CMR requests
 * @throws {Error} If no token is found. CMR will not return anything
 * if no token is supplied.
 */
 exports.getEchoToken = async () => {
    console.log(process.env.CMR_ENVIRONMENT);
    const response = getSecureParam(
      `/${process.env.CMR_ENVIRONMENT}/browse-scaler/CMR_ECHO_SYSTEM_TOKEN`
    );
  
    if (response === undefined) {
      throw new Error('ECHO Token not found. Please update config!');
    } else {
      console.log('Retrieved ECHO TOKEN');
    }
  
    return response;
  };