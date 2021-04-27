const fetch = require('node-fetch');

/**
 * Given a CMR Scroll Session ID, clear that session from
 * memory, freeing up more CMR resources
 * @param {String} scrollId 
 * @returns Status code response
 */
exports.clearScrollSession = async scrollId => {
    const response = await fetch(`${process.env.CMR_ROOT}/search/clear-scroll`, {
      method: 'POST',
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({'scroll_id': scrollId})
    }).then(response => response.status);
  
    console.log(`Cleared scroll session [${scrollId}]. Status code was: ${response}`);
  
    return response;
  }