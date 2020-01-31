// config.js

const CMR_SEARCH_API = process.env.CMR_API || 'https://cmr.earthdata.nasa.gov/search';


const REDIS_HOST = process.env.REDIS_URL || 'localhost';
const REDIS_PORT = process.env.REDIS_PORT || 6379;
const { REDIS_PASSWORD } = process.env;

const ELASTICSEARCH_HOST = process.env.ELASTICSEARCH_URL || 'localhost';
const ELASTICSEARCH_PORT = process.env.ELASTICSEARCH_PORT || 9200;
const { ELASTICSEARCH_VERSION } = process.env;

module.exports = {
  CMR_SEARCH_API,

  ELASTICSEARCH_HOST,
  ELASTICSEARCH_PORT,
  ELASTICSEARCH_VERSION,

  REDIS_HOST,
  REDIS_PORT,
  REDIS_PASSWORD,
};
