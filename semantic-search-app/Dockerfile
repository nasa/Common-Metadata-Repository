FROM python:3.13-slim
WORKDIR /app
COPY pyproject.toml README.md ./
COPY semantic_search ./semantic_search
RUN pip install --no-cache-dir .
USER 65532:65532
EXPOSE 8080
CMD ["uvicorn", "semantic_search.main:app", "--host", "0.0.0.0", "--port", "8080"]

