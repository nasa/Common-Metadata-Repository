// suggester.es1.js

const Suggester = require('./Suggester');

const LOG = require('./logger');

/**
 * Elasticsearch 1.x suggester. This suggester is optimized for edge_ngram indexes
 * matching a schema of {@link Suggestion}
 *
 * @class ES1Suggester
 */
class ES1Suggester extends Suggester {
  /**
   * @constructor ES1Suggester
   * @param {object} opts
   * @param {ElasticsearchClient} opts.client
   * @param {string} opts.index
   */
  constructor(opts) {
    super();

    this.client = opts.client;
    this.index = opts.index;
  }

  /**
   * Elasticsearch 1.x uses an edge_ngram index to perform suggestions
   *
   * @param {string} query
   * @returns {Promise<Array.<Suggestion>>}
   */
  async autocomplete(query) {
    try {
      const response = await this.client.search({
        index: this.index,
        type: 'suggestion',
        q: `value:${query}`,
      });

      const { body } = response;

      return body.hits.hits.map((hit) => ({
        /* eslint-disable no-underscore-dangle  */
        type: hit._source.type,
        value: hit._source.value,
        score: hit._score,
        /* eslint-enable no-underscore-dangle  */
      }));
    } catch (err) {
      LOG.error(`Could not fetch results from Elasticsearch: ${err.message}`);
      return [];
    }
  }
}

module.exports = ES1Suggester;
