// Suggester.js

/**
 * @typedef Suggestion
 *
 * @property {string} type Type of recommendation
 * @property {string} value Text of the recommendation
 * @property {number} [score] Match value based on underlying system
 */

/**
 * Suggester superclass
 * @interface
 */
class Suggester {
  /**
   * Function to override in each implementation.
   *
   * @param {string} q
   * @param {object} [opts]
   * @returns {Promise<Array.<Suggestion>>}
   */
  // eslint-disable-next-line no-unused-vars, class-methods-use-this
  async autocomplete(q, opts) {
    throw new Error('No implementation for suggest. Please use concrete implementation');
  }
}

module.exports = Suggester;
