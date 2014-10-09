(ns cmr.system-trace.http
  "Provides functions for communicating and extracting trace information across HTTP"
  (:require [cmr.system-trace.context :as c]))

(def TRACE_ID_HEADER
  "The HTTP header field containing the current trace id."
  "cmr-trace-id")

(def SPAN_ID_HEADER
  "The HTTP header field containing the current span id. Span name is not sent over HTTP because
  it's not needed on the other side. The span-id sent over the wire will become the parent span id
  of any new spans in the service being invoked"
  "cmr-span-id")

(defn build-request-context
  "Creates a request context. Takes the current system and an HTTP Request"
  [system request]
  (let [{{^String trace-id TRACE_ID_HEADER
          ^String span-id SPAN_ID_HEADER} :headers} request]
    (if (and trace-id span-id)
      (c/request-context system (c/trace-info (Long. trace-id) (Long. span-id)))
      (c/request-context system (c/trace-info)))))

(defn build-request-context-handler
  "This is a ring handler that will extract trace info from the current request."
  [f system]
  (fn [request]
    (f (assoc request :request-context (build-request-context system request)))))

(defn context->http-headers
  "Converts a request context into a map of HTTP headers that need to be sent."
  [context]
  (let [{:keys [span-id trace-id]} (c/context->trace-info context)]
    (if (and span-id trace-id)
      {TRACE_ID_HEADER (str trace-id)
       SPAN_ID_HEADER (str span-id)}
      {})))