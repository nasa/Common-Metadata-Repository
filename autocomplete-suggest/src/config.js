// config.js

const CMR_SEARCH_API = process.env.CMR_API;

const ES_HTTP_SCHEMA = process.env.ES_HTTP_SCHEMA || 'http';
const ES_HOST = process.env.ES_HOST || 'localhost';
const ES_PORT = process.env.ES_PORT || '9200';
const ES_INDEX = process.env.ES_INDEX || 'autocomplete';
const { ES_API_KEY } = process.env;
const { ES_VERSION } = process.env;

const LOG_LEVEL = process.env.LOG_LEVEL || 'info';

module.exports = {
  CMR_SEARCH_API,

  ES_VERSION,
  ES_INDEX,
  ES_HOST,
  ES_PORT,
  ES_HTTP_SCHEMA,
  ES_API_KEY,

  LOG_LEVEL,
};

