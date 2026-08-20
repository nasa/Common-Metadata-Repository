# CMR semantic search prototype

Internal FastAPI service for importing flattened CMR metadata from S3 and searching a standalone
Elasticsearch vector index. See `GET /version` for its runtime configuration contract.

Run locally with `uvicorn semantic_search.main:app --reload` after setting `ELASTICSEARCH_URL` and
AWS credentials. Imports require `s3:GetObject`; embedding requires `bedrock:InvokeModel`.

