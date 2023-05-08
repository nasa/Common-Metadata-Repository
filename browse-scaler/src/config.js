/**
 * convert value from a string to an integer with an optional radix.
 * returns null on error.
 */
function strToInt(strVal, radix = 10) {
  const intValue = parseInt(strVal, radix);
  // eslint-disable-next-line no-restricted-globals
  if (isNaN(intValue)) {
    return null;
  }
  return intValue;
}

export const TIMEOUT_INTERVAL = process.env.TIMEOUT_INTERVAL || 10000;

const { CMR_ROOT } = process.env;
// CMR operational environments uses internal LB as CMR root, so we use http, not https
const CMR_ROOT_URL = `http://${CMR_ROOT}`;

export const { CMR_ENVIRONMENT } = process.env;
const _CMR_ROOT = CMR_ROOT;
export { _CMR_ROOT as CMR_ROOT };
const _CMR_ROOT_URL = CMR_ROOT_URL;
export { _CMR_ROOT_URL as CMR_ROOT_URL };
export const CMR_COLLECTION_URL = `${CMR_ROOT_URL}/search/collections.json?concept_id=`;
export const CMR_GRANULE_URL = `${CMR_ROOT_URL}/search/granules.json?concept_id=`;
export const { CMR_ECHO_TOKEN } = process.env;

export const AWS_REGION = process.env.AWS_REGION || 'us-east-1';

export const REDIS_URL = process.env.REDIS_URL || 'localhost';
export const REDIS_PORT = strToInt(process.env.REDIS_PORT) || 6379;
export const REDIS_KEY_EXPIRE_SECONDS = strToInt(process.env.REDIS_KEY_EXPIRE_SECONDS) || 84000;
