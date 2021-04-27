const { bootstrapGremilinServer } = require('../utils/bootstrapGremlinServer');

module.exports.bootstrap = async event => {
  await bootstrapGremilinServer();
  return {
    statusCode: 200,
    body: "Indexing completed"
  };
};
