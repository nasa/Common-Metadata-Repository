// handler.spec.js

// const redis = require('redis');
// const suggest = require('./suggest');

const { suggestHandler } = require('./handler');

// *****************************************************
// TODO known issue with jest mocking redis
// https://github.com/facebook/jest/issues/8983
// disabling test for now
// *****************************************************

// jest.mock('redis');
// jest.mock('./suggest');
//
// redis.createClient.mockReturnValue(jest.mock());
// suggest.mockResolvedValue(
//   {
//     type: 'instrument',
//     suggestions: [
//       'ice auger',
//     ],
//   },
// );

test.skip('it returns a SuggestionResponse', async () => {
  const event = {
    queryStringParameters: {
      q: 'ice',
    },
  };

  const response = await suggestHandler(event);

  expect(response.statusCode).toEqual(200);

  const body = JSON.parse(response.body);
  expect(body.query).toEqual('ice');
  expect(Array.isArray(body.results)).toBeTruthy();
});
