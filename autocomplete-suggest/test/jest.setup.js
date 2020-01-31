// jest.setup.js

const LOG = require('../src/logger');

// disable log output during tests
LOG.silent = true;
