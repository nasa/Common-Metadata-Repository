import os
import logging
import sys

LOG_LEVEL = os.getenv("LOG_LEVEL")
if not LOG_LEVEL:
    LOG_LEVEL = logging.INFO

def setup_logger(name, log_file=None, level=logging.INFO):
    """Function to setup as many loggers as you want"""

    formatter = logging.Formatter('%(asctime)s %(levelname)s %(message)s')
    
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(formatter)

    logger = logging.getLogger(name)
    logger.setLevel(level)
    logger.addHandler(handler)

    if log_file:
        file_handler = logging.FileHandler(log_file)
        file_handler.setFormatter(formatter)
        logger.addHandler(file_handler)

    return logger

# Create a default logger
logger = setup_logger(name='default_logger', level=LOG_LEVEL)
