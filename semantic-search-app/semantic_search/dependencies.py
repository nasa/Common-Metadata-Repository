import asyncio
import json
import random

import boto3
from elasticsearch import AsyncElasticsearch


class BedrockUnavailable(RuntimeError):
    pass


class BedrockEmbedder:
    def __init__(self, client, model_id: str, max_attempts: int):
        self.client, self.model_id, self.max_attempts = client, model_id, max_attempts

    async def embed(self, text: str) -> tuple[list[float], int]:
        for attempt in range(self.max_attempts):
            try:
                response = await asyncio.to_thread(self.client.invoke_model, modelId=self.model_id,
                    contentType="application/json", accept="application/json",
                    body=json.dumps({"inputText": text, "dimensions": 1024, "normalize": True}))
                body = json.loads(response["body"].read())
                vector = body["embedding"]
                if len(vector) != 1024:
                    raise BedrockUnavailable("Bedrock returned an invalid embedding")
                return vector, int(body.get("inputTextTokenCount", 0))
            except Exception as error:
                code = getattr(error, "response", {}).get("Error", {}).get("Code", "")
                if code not in {"ThrottlingException", "ServiceUnavailableException", "InternalServerException"} or attempt + 1 == self.max_attempts:
                    raise BedrockUnavailable("Bedrock embedding unavailable") from error
                await asyncio.sleep((2**attempt) * 0.1 + random.random() * 0.1)
        raise BedrockUnavailable("Bedrock embedding unavailable")


def build_dependencies(settings):
    session = boto3.session.Session(region_name=settings.aws_region)
    s3 = session.client("s3")
    bedrock = session.client("bedrock-runtime")
    kwargs = {}
    if settings.elasticsearch_api_key:
        kwargs["api_key"] = settings.elasticsearch_api_key
    elif settings.elasticsearch_username:
        kwargs["basic_auth"] = (settings.elasticsearch_username, settings.elasticsearch_password or "")
    es = AsyncElasticsearch(settings.elasticsearch_url, **kwargs)
    return s3, BedrockEmbedder(bedrock, settings.bedrock_model_id, settings.bedrock_max_attempts), es

