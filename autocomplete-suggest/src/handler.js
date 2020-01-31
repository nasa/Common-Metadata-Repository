// handler.js

const elasticsearch = require('elasticsearch');

const LOG = require('./logger');

const {
  ELASTICSEARCH_HOST,
  ELASTICSEARCH_PORT,
  ELASTICSEARCH_VERSION,
} = require('./config');


const HTTP_OK = 200;
const HTTP_CLIENT_ERROR = 400;
const HTTP_SERVER_ERROR = 500;

/**
 *
 * @param message
 * @param statusCode
 * @returns {{body: string, statusCode: *}}
 */
const buildResult = (message, statusCode) => {
  const body = typeof message === 'string' ? message : JSON.stringify(message);

  return {
    statusCode,
    body,
  };
};

/**
 *
 * @param event
 * @returns {Promise<{body: string, statusCode: *}>}
 */
module.exports.suggestHandler = async (event) => {
  const { q, types } = event.queryStringParameters;

  if (!q) {
    return buildResult(
      {
        message: 'Missing query value',
        usage: '/autocomplete?q=<query>[&types=<comma separated types>]',
      },
      HTTP_CLIENT_ERROR,
    );
  }

  const esClient = new elasticsearch.Client({
    host: `${ELASTICSEARCH_HOST}:${ELASTICSEARCH_PORT}`,
    apiVersion: ELASTICSEARCH_VERSION,
  });

  try {
    const response = await esClient.search({
      index: 'autocomplete',
      type: '_doc',
      body: {
        query: {
          match: {
            facets: q,
          },
        },
      },
    });

    return buildResult(
      {
        query: q,
        results: response.hits.hits,
      },
      HTTP_OK,
    );
  } catch (err) {
    LOG.error(`An error occurred generating suggestions: ${err.message}`);

    return buildResult(
      {
        message: 'A problem occurred while generating suggestions',
      },
      HTTP_SERVER_ERROR,
    );
  }
};
