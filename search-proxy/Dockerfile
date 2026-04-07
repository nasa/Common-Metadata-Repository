FROM python:3.11-slim AS builder

WORKDIR /build
COPY pyproject.toml .
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --no-cache-dir .

FROM python:3.11-slim

RUN groupadd -r proxy && useradd -r -g proxy proxy
WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY src/proxy/ proxy/
COPY lanes.json .

ENV PATH="/opt/venv/bin:$PATH"

USER proxy
EXPOSE 3013
