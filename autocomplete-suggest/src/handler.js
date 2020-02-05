// handler.js

const { Client } = require('@elastic/elasticsearch');
const SuggesterFactory = require('./SuggesterFactory');

const LOG = require('./logger');

const {
  ES_INDEX,
  ES_VERSION,
  ES_HOST,
  ES_PORT,
  ES_HTTP_SCHEMA,
} = require('./config');

const HTTP_OK = 200;
const HTTP_CLIENT_ERROR = 400;
const HTTP_SERVER_ERROR = 500;

/**
 *
 * @param payload
 * @param statusCode
 * @returns {{body: string, statusCode: *}}
 */
const buildResponse = (payload, statusCode) => {
  const body = JSON.stringify(payload);
  return {
    statusCode,
    body,
  };
};

/**
 * Sorts suggestions by score in descending order
 *
 * @param {Suggestion} a
 * @param {Suggestion} b
 * @returns {number}
 */
const byScoreDescending = (a, b) => b.score - a.score;

/**
 *
 * @param event
 * @returns {Promise<{body: string, statusCode: *}>}
 */
module.exports.suggestHandler = async (event) => {
  const { q: query } = event.queryStringParameters;

  if (!query) {
    return buildResponse(
      {
        message: 'Missing query value',
        usage: '/autocomplete?q=<query>[&types=<comma separated types>]',
      },
      HTTP_CLIENT_ERROR,
    );
  }

  // Connect to Elasticsearch
  LOG.debug(`Elasticsearch: ${ES_HTTP_SCHEMA}://${ES_HOST}:${ES_PORT}`);
  const esClient = new Client(
    {
      node: `${ES_HTTP_SCHEMA}://${ES_HOST}:${ES_PORT}`,
    },
  );

  const suggester = SuggesterFactory.create(
    ES_VERSION,
    {
      client: esClient,
      index: ES_INDEX,
    },
  );

  try {
    LOG.debug(`Autocompleting from [${query}]`);
    const results = await suggester.autocomplete(query);

    results.sort(byScoreDescending);
    LOG.silly(results);

    return buildResponse(
      {
        query,
        results,
      },
      HTTP_OK,
    );
  } catch (err) {
    LOG.error(`An error occurred generating suggestions: ${err.message}`);

    return buildResponse(
      {
        message: 'A problem occurred while generating suggestions',
      },
      HTTP_SERVER_ERROR,
    );
  }
};

/**
 *
 * @param event
 * @returns {Promise<void>}
 */
module.exports.updateSuggestionsHandler = async (event) => {
  const payload = {
    message: 'Updating autocomplete indices',
  };

  LOG.debug(`Elasticsearch: ${ES_HTTP_SCHEMA}://${ES_HOST}:${ES_PORT}`);
  const esClient = new Client(
    {
      node: `${ES_HTTP_SCHEMA}://${ES_HOST}:${ES_PORT}`,
    },
  );

  const { body: bulkResponse } = esClient.bulk({
    refresh: true,
    body: [],
  });


  if (bulkResponse.errors) {
    LOG.error(bulkResponse);

    return buildResponse(
      {
        message: 'operation failed',
      },
      HTTP_SERVER_ERROR,
    );
  }

  return buildResponse(payload, HTTP_OK);
};
