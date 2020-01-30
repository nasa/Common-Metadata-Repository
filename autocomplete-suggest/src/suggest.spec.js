// suggest.spec.js
const FlexSearch = require('flexsearch');
const suggest = require('./suggest');

const mockRedis = {};

const idx = new FlexSearch('speed');
idx.add(0, 'ice');
const strIdx = idx.export();

// Setup
beforeEach(() => {
  mockRedis.getAsync = jest
    .fn()
    .mockResolvedValue(strIdx);
});

test('it should return a SuggestionCollection', async () => {
  const result = await suggest(mockRedis, 'instrument', 'scan');

  expect(result).toHaveProperty('type');
  expect(result.type).toEqual('instrument');

  expect(result).toHaveProperty('suggestions');
  expect(Array.isArray(result.suggestions)).toBeTruthy();
});


test('it should get suggestions from redis', async () => {
  await suggest(mockRedis, 'instrument', 'ice');
  expect(mockRedis.getAsync).toHaveBeenCalled();
});
