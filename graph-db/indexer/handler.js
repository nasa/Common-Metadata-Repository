'use strict';

module.exports.index = async event => {
  console.log(`Ryan, Sarah and Mark will see ingest event: [${JSON.stringify(event, null, 2)}].`)
  return {
    statusCode: 200,
    body: JSON.stringify(
      {
        message: 'Processed ingest event successfully!',
        input: event,
      },
      null,
      2
    ),
  };
};
