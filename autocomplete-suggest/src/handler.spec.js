// handler.spec.js

jest.mock('./suggest');

const redis = require('redis');
const suggest = require('./suggest');

const { suggestHandler } = require('./handler');

// *****************************************************
// TODO known issue with jest mocking redis module
// https://github.com/facebook/jest/issues/8983
// monkey-patch redis for now
// *****************************************************
const keys = JSON.stringify(['instrument', 'platform']);
redis.createClient = jest
  .fn()
  .mockReturnValue({
    on: jest.fn(),
    quit: jest.fn(),
    getAsync: jest.fn().mockResolvedValue(keys),
  });

test(
  'GIVEN a query WHEN the user GETS the endpoint THEN it returns a SuggestionResponse',
  async () => {
    // GIVEN
    suggest.mockResolvedValue(
      {
        type: 'instrument',
        suggestions: [
          'ice auger',
        ],
      },
    );

    const event = {
      queryStringParameters: {
        q: 'ice',
      },
    };

    // WHEN
    const response = await suggestHandler(event);

    // THEN
    expect(response.statusCode).toEqual(200);

    const body = JSON.parse(response.body);
    expect(body.query).toEqual('ice');
    expect(Array.isArray(body.results)).toBeTruthy();
  },
);

test(
  'GIVEN a query and type list WHEN the user GETS the endpoint THEN it returns a SuggestionResponse',
  async () => {
    // GIVEN
    const event = {
      queryStringParameters: {
        q: 'ice',
        types: 'instrument,platform',
      },
    };

    suggest.mockResolvedValueOnce(
      {
        type: 'instrument',
        suggestions: [
          'ice auger',
        ],
      },
    ).mockResolvedValueOnce(
      {
        type: 'instrument',
        suggestions: [
          'ice auger',
        ],
      },
    );

    // WHEN
    const response = await suggestHandler(event);

    // THEN
    expect(response.statusCode).toEqual(200);

    const body = JSON.parse(response.body);
    expect(body.query).toEqual('ice');
    expect(Array.isArray(body.results)).toBeTruthy();
  },
);

test(
  'GIVEN no query string WHEN the user GETS the endpoint THEN it should return a 400 status and message',
  async () => {
    // GIVEN
    const event = {
      queryStringParameters: {},
    };

    // WHEN
    const response = await suggestHandler(event);

    // THEN
    expect(response.statusCode).toEqual(400);

    const body = JSON.parse(response.body);
    expect(body.message).toMatch(/missing query/i);
  },
);

test(
  'GIVEN a server error WHEN the user GETS the endpoint THEN it should return a 500 status and message',
  async () => {
    // GIVEN
    const event = {
      queryStringParameters: {
        q: 'ice',
        types: 'instrument,platform',
      },
    };

    suggest.mockImplementation(() => {
      throw new Error('This is a fake error for testing');
    });

    // WHEN
    const response = await suggestHandler(event);

    // THEN
    expect(response.statusCode).toEqual(500);

    const body = JSON.parse(response.body);
    expect(body.message).toMatch(/a problem/i);
  },
);
