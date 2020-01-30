// logger.js

const winston = require('winston');

const LOG = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new (winston.transports.Console)(),
  ],
});

module.exports = LOG;
