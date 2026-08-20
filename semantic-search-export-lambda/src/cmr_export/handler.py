import json
import logging

import boto3
from elasticsearch import Elasticsearch

from .config import Settings, invocation
from .exporter import run_export

LOG = logging.getLogger(__name__)
LOG.setLevel(logging.INFO)


def clients(settings):
    options = {"verify_certs": settings.verify_certs, "request_timeout": 60}
    if settings.api_key:
        options["api_key"] = settings.api_key
    elif settings.username or settings.password:
        if not settings.username or not settings.password:
            raise ValueError("both ELASTICSEARCH_USERNAME and ELASTICSEARCH_PASSWORD are required")
        options["basic_auth"] = (settings.username, settings.password)
    return Elasticsearch(settings.elasticsearch_url, **options), boto3.client("s3")


def lambda_handler(event, _context, *, es=None, s3=None, settings=None):
    try:
        settings = settings or Settings.from_env()
        bucket, key, providers, limit = invocation(event, settings)
        if es is None or s3 is None:
            default_es, default_s3 = clients(settings)
            es, s3 = es or default_es, s3 or default_s3
        return run_export(es, s3, settings, bucket, key, providers, limit)
    except Exception as error:
        LOG.exception(json.dumps({"event": "export_failed", "category": type(error).__name__}))
        raise
