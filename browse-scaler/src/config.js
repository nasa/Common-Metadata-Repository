/**
 * convert value from a string to an integer with an optional radix.
 * returns null on error.
 */
function strToInt (strVal, radix = 10) {
    const intValue = parseInt (strVal, radix);

    if (isNaN (intValue)) {
        return null;
    }
    return intValue;
}

exports.TIMEOUT_INTERVAL =  strToInt (process.env.EXTERNAL_REQUEST_TIMEOUT) || 1000;

const CMR_ROOT = process.env.CMR_ROOT;
// CMR operational envirionments uses internal LB as CMR root, so we use http, not https
const CMR_ROOT_URL = `http://${CMR_ROOT}`;

/* CMR ENVIRONMENT VARIABLES */
exports.CMR_ENVIRONMENT = process.env.CMR_ENVIRONMENT;
exports.CMR_ROOT = CMR_ROOT;
exports.CMR_ROOT_URL = CMR_ROOT_URL;
exports.CMR_COLLECTION_URL = `${CMR_ROOT_URL}/search/collections.json?concept_id=`;
exports.CMR_GRANULE_URL = `${CMR_ROOT_URL}/search/granules.json?concept_id=`;
exports.CMR_ECHO_TOKEN = process.env.CMR_ECHO_TOKEN;

/* AWS Config */
exports.AWS_REGION = process.env.AWS_REGION || 'us-east-1';

/* REDIS config */
// URL is incorrect and should be changed to HOST
exports.REDIS_URL = process.env.REDIS_URL || 'localhost';
exports.REDIS_PORT = strToInt (process.env.REDIS_PORT) || 6379;
exports.REDIS_KEY_EXPIRE_SECONDS = strToInt (process.env.REDIS_KEY_EXPIRE_SECONDS) || 84000;
