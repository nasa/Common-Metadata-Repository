// suggester.es7.spec.js

jest.mock('@elastic/elasticsearch');

const es = require('@elastic/elasticsearch');

es.Client = jest.fn().mockImplementation(() => ({
  search: jest.fn(),
}));

const esClient = new es.Client({ node: 'http://foo:9200' });

const ES1Suggester = require('./suggester.es1');

const ES7_RESPONSE_GOOD = {
  body: {
    took: 10,
    timed_out: false,
    _shards: {
      total: 1,
      successful: 1,
      skipped: 0,
      failed: 0,
    },
    hits: {
      total: {
        value: 1,
        relation: 'eq',
      },
      max_score: 0.2876821,
      hits: [
        {
          _index: 'autocomplete',
          _type: '_doc',
          _id: 'giS6C3AB9ztWDttQgPW_',
          _score: 0.2876821,
          _source: {
            type: 'instrument',
            value: 'ice auger',
          },
        },
      ],
    },
  },
};
const ES7_RESPONSE_EMPTY = {
  body: {
    took: 10,
    timed_out: false,
    _shards: {
      total: 1,
      successful: 1,
      skipped: 0,
      failed: 0,
    },
    hits: {
      total: {
        value: 0,
        relation: 'eq',
      },
      max_score: 0,
      hits: [],
    },
  },
};

let suggester;
beforeEach(() => {
  suggester = new ES1Suggester({ client: esClient, index: 'autocomplet' });
});

test(
  'GIVEN an ES client WHEN a query is made THEN elasticsearch is queried',
  () => {
    suggester.autocomplete('foo');
    expect(esClient.search).toBeCalledWith(expect.anything());
  },
);

test(
  'GIVEN es returns hits WHEN queried THEN an array of suggestions is returned',
  async () => {
    // GIVEN
    esClient.search = jest.fn().mockResolvedValue(ES7_RESPONSE_GOOD);

    const results = await suggester.autocomplete('foo');
    expect(results).toHaveLength(1);

    const result = results[0];
    expect(result).toHaveProperty('type', 'instrument');
    expect(result).toHaveProperty('value', 'ice auger');
    expect(result).toHaveProperty('score', 0.2876821);
  },
);

test(
  'GIVEN es returns no hits WHEN autocomplete THEN an empty array is returned',
  async () => {
    // GIVEN
    esClient.search = jest.fn().mockResolvedValue(ES7_RESPONSE_EMPTY);

    const results = await suggester.autocomplete('bar');
    expect(results).toHaveLength(0);
  },
);
