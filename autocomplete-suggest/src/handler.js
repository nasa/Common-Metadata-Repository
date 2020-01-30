// handler.js

const bluebird = require('bluebird');
const redis = require('redis');

const LOG = require('./logger');
const suggest = require('./suggest');

const {
  REDIS_HOST,
  REDIS_PORT,
  REDIS_PASSWORD,
} = require('./config');

bluebird.promisifyAll(redis.RedisClient.prototype);
bluebird.promisifyAll(redis.Multi.prototype);

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
 * Configures a redis client
 * @returns {RedisClient}
 */
const createRedisClient = () => {
  const redisOpts = {
    password: REDIS_PASSWORD,
  };

  const client = redis.createClient(
    REDIS_PORT,
    REDIS_HOST,
    redisOpts,
  );

  client.on('connect', () => LOG.debug(`Redis client connected to ${REDIS_HOST}:${REDIS_PORT}`));
  client.on('end', () => LOG.debug('Redis client disconnected'));

  client.on('error', (err) => {
    const { message } = err;
    LOG.error(`An error occurred with the redis connection: ${message}`);
  });

  return client;
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

  const client = createRedisClient();

  let redisTypeKeys;
  if (Array.isArray(types) && types.length) {
    redisTypeKeys = types.map((t) => `AUTOCOMPLETE_FACET_KEY_${t}`);
  } else {
    redisTypeKeys = await client.getAsync('AUTOCOMPLETE_FACET_KEY_LIST');
  }

  try {
    const results = await Promise.all(redisTypeKeys.map((key) => suggest(client, q, key)));

    return buildResult(
      {
        query: q,
        results,
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
  } finally {
    client.quit();
  }
};
