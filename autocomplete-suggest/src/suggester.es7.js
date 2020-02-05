// suggester.es7.js

const Suggester = require('./Suggester');
const LOG = require('./logger');

class ES7Suggester extends Suggester {
  /**
   *
   * @param {objects} opts
   * @param {ElasticsearchClient} opts.client
   * @param {string} opts.index
   */
  constructor(opts) {
    super();

    this.client = opts.client;
    this.index = opts.index;
  }

  /**
   * Elasticsearch > 7.x supports a `search_as_you_type` type that is purpose made for
   * autocompletion. This suggester takes advantage of this and searches custom
   * autocomplete indexes using the recommended query.
   *
   * @param {string} query
   * @returns {Promise<Array.<Suggestion>>}
   */
  async autocomplete(query) {
    try {
      const { body: response } = await this.client.search({
        index: this.index,
        body: {
          query: {
            multi_match: {
              query,
              type: 'bool_prefix',
              fields: [
                'value',
                'value._2gram',
                'value._3gram',
              ],
            },
          },
        },
      });

      return response.hits.hits.map((hit) => ({
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

module.exports = ES7Suggester;
