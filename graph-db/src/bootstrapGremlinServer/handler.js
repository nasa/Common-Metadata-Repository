const { bootstrapGremilinServer } = require('../utils/bootstrapGremlinServer');

module.exports.bootstrap = async event => {
  let body = await bootstrapGremilinServer();
  return {
    statusCode: 200,
    body: ":)"
  };
};
