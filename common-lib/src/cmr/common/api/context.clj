(ns cmr.common.api.context
  "Contains functions for creating a request context.

  Main functions in the CMR system are expected to accept a context object. The shape of a context
  map is the following

  {:system {
            ;; system level items. (db, cache, etc.)
           }
   :request {;; fields here related to current request like token or current user.
            }
  }"
  (:require [cmr.common.log :as log]))

(defn request-context
  "Creates a new request context with the given system and request id"
  [system request-id]
  {:system system
   :request {:request-id (or request-id (str (java.util.UUID/randomUUID)))}})

(defn context->request-id
  "Extracts request id from a request context."
  [context]
  (get-in context [:request :request-id]))

(def REQUEST_ID_HEADER
  "The HTTP header field containing the current request id."
  "CMR-Request-Id")

(defn build-request-context
  "Creates a request context. Takes the current system and an HTTP Request"
  [system request]
  (let [{{request-id REQUEST_ID_HEADER} :headers} request]
    (request-context system request-id)))

(defn build-request-context-handler
  "This is a ring handler that will extract trace info from the current request."
  [f system]
  (fn [request]
    (let [context (build-request-context system request)]
      (log/with-request-id
        (context->request-id context)
        (f (assoc request :request-context context))))))

(defn context->http-headers
  "Converts a request context into a map of HTTP headers that need to be sent."
  [context]
  (when-let [request-id (context->request-id context)]
    {REQUEST_ID_HEADER request-id}))

