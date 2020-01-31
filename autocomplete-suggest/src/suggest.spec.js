// suggest.spec.js
const FlexSearch = require('flexsearch');
const suggest = require('./suggest');

const mockRedis = {};

const instrumentIndex = new FlexSearch('speed');
instrumentIndex.add(0, 'ice');
instrumentIndex.add(1, 'icesat');
const strIdx = instrumentIndex.export();

// Setup
beforeEach(() => {
  mockRedis.getAsync = jest
    .fn()
    .mockResolvedValue(strIdx);
});

test('GIVEN valid input WHEN a query is made THEN it should return a SuggestionCollection', async () => {
  const result = await suggest(mockRedis, 'instrument', 'scan');

  expect(result).toHaveProperty('type');
  expect(result.type).toEqual('instrument');

  expect(result).toHaveProperty('suggestions');
  expect(Array.isArray(result.suggestions)).toBeTruthy();
});


test('GIVEN a redis client WHEN queried THEN it should pull from redis', async () => {
  await suggest(mockRedis, 'instrument', 'ice');
  expect(mockRedis.getAsync).toHaveBeenCalled();
});

test('GIVEN redis WHEN redis returns nothing THEN an empty suggestions array is returned', async () => {
  // GIVEN
  mockRedis.getAsync.mockResolvedValue(null);

  // WHEN
  const result = await suggest(mockRedis, 'instrument', 'auger');

  // THEN
  expect(result).toHaveProperty('suggestions');
  expect(result.suggestions.length).toBe(0);
});

test('GIVEN redis errors out WHEN redis returns nothing THEN an empty suggestions array is returned', async () => {
  // GIVEN
  mockRedis.getAsync.mockImplementation(() => {
    throw new Error('Fake redis connection error');
  });

  // WHEN
  const result = await suggest(mockRedis, 'provider', 'ORNL_DAA');

  // THEN
  expect(result).toHaveProperty('suggestions');
  expect(result.suggestions.length).toBe(0);
});
