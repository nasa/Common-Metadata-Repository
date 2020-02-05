// logger.js

const winston = require('winston');

const { LOG_LEVEL } = require('./config');

const LOG = winston.createLogger({
  level: LOG_LEVEL,
  format: winston.format.json(),
  transports: [
    new (winston.transports.Console)(),
  ],
});

module.exports = LOG;
