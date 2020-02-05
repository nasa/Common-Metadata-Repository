// handler.spec.js

const { suggestHandler } = require('./handler');
const SuggesterFactory = require('./SuggesterFactory');

jest.mock('./SuggesterFactory');

const mockSuggester = {
  autocomplete: jest.fn(),
};
SuggesterFactory.create.mockReturnValue(mockSuggester);

test(
  'GIVEN a valid query and connected to Elasticsearch 1.x WHEN activated THEN a 200 response is returned',
  async () => {
    // GIVEN
    const event = {
      queryStringParameters: {
        q: 'foo',
      },
    };

    mockSuggester.autocomplete.mockResolvedValue([
      {
        type: 'instrument',
        value: 'foo scope',
        score: 0.314159,
      },
    ]);


    // WHEN
    const response = await suggestHandler(event);

    // THEN
    expect(response.statusCode).toEqual(200);

    const body = JSON.parse(response.body);
    expect(body.query).toEqual('foo');
    expect(Array.isArray(body.results)).toBeTruthy();
  },
);
