const CMR_SEARCH_API = process.env.CMR_API || 'https://cmr.earthdata.nasa.gov/search';

const REDIS_HOST = process.env.REDIS_URL || 'localhost';
const REDIS_PORT = process.env.REDIS_PORT || 6379;
const { REDIS_PASSWORD } = process.env;

module.exports = {
  CMR_SEARCH_API,
  REDIS_HOST,
  REDIS_PORT,
  REDIS_PASSWORD,
};
