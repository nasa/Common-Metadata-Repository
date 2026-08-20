import logging
from contextlib import asynccontextmanager

from elasticsearch import ApiError
from fastapi import FastAPI, HTTPException, Query, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.encoders import jsonable_encoder
from fastapi.responses import JSONResponse

from . import __version__
from .config import get_settings
from .dependencies import BedrockUnavailable, build_dependencies
from .importer import ImportManager
from .models import ImportRequest
from .search import search

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
LOG = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    s3, embedder, es = build_dependencies(settings)
    app.state.settings, app.state.embedder, app.state.es = settings, embedder, es
    app.state.imports = ImportManager(settings, s3, embedder, es)
    await app.state.imports.initialize()
    yield
    await es.close()


app = FastAPI(title="CMR Semantic Search", version=__version__, lifespan=lifespan)


@app.exception_handler(RequestValidationError)
async def validation_error(_request, error):
    return JSONResponse(status_code=400, content={"detail": jsonable_encoder(error.errors())})


@app.exception_handler(BedrockUnavailable)
async def bedrock_error(_request, _error):
    LOG.warning("dependency_failure category=bedrock")
    return JSONResponse(status_code=503, content={"detail": "embedding service unavailable"})


@app.exception_handler(ApiError)
async def elastic_error(_request, _error):
    LOG.warning("dependency_failure category=elasticsearch")
    return JSONResponse(status_code=502, content={"detail": "search dependency unavailable"})


@app.post("/imports", status_code=status.HTTP_202_ACCEPTED)
async def submit_import(body: ImportRequest, request: Request):
    try:
        job = await request.app.state.imports.submit(body.s3_uri)
    except RuntimeError as error:
        raise HTTPException(409, str(error)) from error
    return {**job.model_dump(mode="json"), "status_url": f"/imports/{job.import_id}"}


@app.get("/imports/{import_id}")
async def import_status(import_id: str, request: Request):
    job = await request.app.state.imports.get(import_id)
    if not job:
        raise HTTPException(404, "import not found")
    return job


@app.get("/semantic-collections")
async def semantic_collections(request: Request, q: str = Query(min_length=1, max_length=1000),
                               mode: str = Query("hybrid", pattern="^(lexical|semantic|hybrid)$"),
                               page_size: int = Query(10, ge=1, le=20), temporal: str | None = None,
                               bounding_box: str | None = None):
    try:
        if not q.strip():
            raise ValueError("q must not be blank")
        result = await search(request.app.state.es, request.app.state.embedder,
                              request.app.state.settings.semantic_index_alias, q.strip(), mode,
                              page_size, temporal, bounding_box)
        LOG.info("search_complete mode=%s took_ms=%s candidate_collections=%s",
                 mode, result["took_ms"], result["candidate_collections"])
        return result
    except ValueError as error:
        raise HTTPException(400, str(error)) from error


@app.get("/health")
async def health(request: Request):
    available = await request.app.state.es.indices.exists_alias(
        name=request.app.state.settings.semantic_index_alias)
    if not available:
        raise HTTPException(503, "semantic index alias unavailable")
    return {"status": "ok"}


@app.get("/version")
async def version(request: Request):
    settings = request.app.state.settings
    return {"version": __version__, "schema_version": 1, "model_id": settings.bedrock_model_id,
            "index_alias": settings.semantic_index_alias}
