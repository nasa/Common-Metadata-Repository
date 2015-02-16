(ns cmr.system-trace.http
  "Provides functions for communicating and extracting trace information across HTTP"
  (:require [cmr.system-trace.context :as c]
            [cmr.common.log :as log]
            [cmr.common.util :as u]))

(def TRACE_ID_HEADER
  "The HTTP header field containing the current trace id."
  "cmr-trace-id")

(def SPAN_ID_HEADER
  "The HTTP header field containing the current span id. Span name is not sent over HTTP because
  it's not needed on the other side. The span-id sent over the wire will become the parent span id
  of any new spans in the service being invoked"
  "cmr-span-id")

(def REQUEST_ID_HEADER
  "The HTTP header field containing the current request id."
  "cmr-request-id")

(defn build-request-context
  "Creates a request context. Takes the current system and an HTTP Request"
  [system request]
  (let [{{^String trace-id TRACE_ID_HEADER
          ^String span-id SPAN_ID_HEADER
          ^String request-id REQUEST_ID_HEADER} :headers} request]
    (if (and trace-id span-id)
      (c/request-context system request-id (c/trace-info (Long. trace-id) (Long. span-id)))
      (c/request-context system request-id (c/trace-info)))))

(defn build-request-context-handler
  "This is a ring handler that will extract trace info from the current request."
  [f system]
  (fn [request]
    (let [context (build-request-context system request)]
      (log/with-request-id
        (c/context->request-id context)
        (f (assoc request :request-context context))))))

(defn context->http-headers
  "Converts a request context into a map of HTTP headers that need to be sent."
  [context]
  (let [{:keys [span-id trace-id]} (c/context->trace-info context)]
    (u/remove-nil-keys
      {TRACE_ID_HEADER (when trace-id (str trace-id))
       SPAN_ID_HEADER (when span-id (str span-id))
       REQUEST_ID_HEADER (c/context->request-id context)})))

