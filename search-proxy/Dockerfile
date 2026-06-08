FROM python:3.11-slim AS builder

WORKDIR /build
COPY src/ src/  
COPY pyproject.toml .
RUN python -m venv /opt/venv && /opt/venv/bin/pip install --no-cache-dir .

FROM python:3.11-slim

WORKDIR /app
COPY --from=builder /opt/venv /opt/venv
COPY src/proxy/ proxy/
COPY lanes.json /lanes.json

ENV PATH="/opt/venv/bin:$PATH"

USER proxy
EXPOSE 3013
