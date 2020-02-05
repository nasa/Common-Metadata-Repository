// SuggesterFactory.spec.js

const SuggesterFactory = require('./SuggesterFactory');
const ES1Suggester = require('./suggester.es1');
const ES7Suggester = require('./suggester.es7');

test('GIVEN elasticsearch version 1 WHEN create is called THEN ES1 client is returned', () => {
  const suggester = SuggesterFactory.create('es1', {
    client: jest.fn(),
    index: 'autocomplete',
  });
  expect(suggester instanceof ES1Suggester).toBeTruthy();
});

test('GIVEN elasticsearch version 7 WHEN create is called THEN ES7 client is returned', () => {
  const suggester = SuggesterFactory.create('es7', {
    client: jest.fn(),
    index: 'autocomplete',
  });
  expect(suggester instanceof ES7Suggester).toBeTruthy();
});

test('GIVEN invalid key WHEN creating THEN null is returned', () => {
  expect(SuggesterFactory.create('foo')).toBeNull();
});
