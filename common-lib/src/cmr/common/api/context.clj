(ns cmr.common.api.context
  "Contains functions for creating or operating on a request context.

  Main functions in the CMR system are expected to accept a context object. The
  shape of a context map is the following.

  {:system {
            ;; system level items. (db, cache, etc.)
           }
   :request {;; fields here related to current request
             ;; like token or current user.
            }}"
  (:require
   [cmr.common.log :as log]
   [cmr.common.util :as util]
   [cmr.common.services.errors :as errors]))

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
  "The HTTP header field containing the current request id. Ring converts all headers to lowercase."
  "cmr-request-id")

(def X_REQUEST_ID_HEADER
  "The HTTP header field containing the current request id. Ring converts all headers to lowercase."
  "x-request-id")

(defn build-request-context
  "Creates a request context. Takes the current system and an HTTP Request"
  [system request]
  (let [{{request-id REQUEST_ID_HEADER x-request-id X_REQUEST_ID_HEADER} :headers} request]
    (request-context system (or request-id x-request-id))))

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

(defn context->user-id
  "Returns user id of the token in the context. Throws an error if no token is
  provided."
  ([context]
   (context->user-id context "Valid user token required."))
  ([context msg]
   (if-let [token (:token context)]
     (util/lazy-get context :user-id)
     (errors/throw-service-error :unauthorized msg))))
