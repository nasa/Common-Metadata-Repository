import requests
import logging
import sys

from parameters import CONFIG
REQUEST_TIMEOUT_SECONDS = 30

logger = logging.getLogger("es")

def es_health_check():
    """
    Get Elastic Search's health status. If the status is green, then return True
    otherwise log an error and exit the program.

    Args:
        None
    
    Returns:
        True if Elastic Search status is green.

    Exceptions:
        None; if there is an exception or Elastic Search is not green then exit the program.
    """
    try:
        response = requests.get(f"{CONFIG.get('GRAN_ELASTIC_URL')}/_cluster/health",
                                 timeout=REQUEST_TIMEOUT_SECONDS)
        response.raise_for_status()
        status = response.json()
        if status.get("status") == "green":
            return True
        else:
            logger.error(f"Elastic Searchs health status is {status.get("status")}. DB ES Audit is exiting.")
            sys.exit(1)
    except Exception:
        logger.exception("Exception from Elastic Search when trying to query its health.")
        sys.exit(1)
