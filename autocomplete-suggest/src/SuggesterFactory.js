// SuggesterFactory.js

const ES1Suggester = require('./suggester.es1');
const ES7Suggester = require('./suggester.es7');

const LOG = require('./logger');

const SUGGESTERS = {
  es1: ES1Suggester,
  es7: ES7Suggester,
};

/**
 * Suggester Factory
 */
class SuggesterFactory {
  /**
   *
   * @param {string} type Type of suggester
   * @param {object} [opts] Configuration object for suggester
   * @returns {Suggester|null}
   */
  static create(type, opts) {
    const t = type.toLowerCase().trim();

    if (SUGGESTERS[t]) {
      LOG.debug(`Building suggester [${t}]`);
      return new SUGGESTERS[t](opts);
    }

    LOG.error(`No such suggester as [${type}]. Available types are ${JSON.stringify(Object.keys(SUGGESTERS))}`);
    return null;
  }
}

module.exports = SuggesterFactory;
