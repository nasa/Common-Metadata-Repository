(ns cmr.common.services.errors
  "Contains functions that will throw errors that when caught in the API will return the
  correct error code.")

(defn- throw-service-error
  "Throws an instance of clojure.lang.ExceptionInfo that will contain a map with the type of
  error and a message. See http://stackoverflow.com/a/16159584."
  ([type msg]
   (throw (ex-info msg {:type type})))
  ([type msg-format & args]
   (throw-service-error type (apply format msg-format args))))


(def type->http-status-code
  {:not-found 404
   :bad-request 400
   :invalid-data 422})


(defn not-found
  "Indicates an item was not found. Takes a message or a message format with args like
  clojure.core/format."
  [& args]
  (apply throw-service-error :not-found args))

(defn bad-request
  "Indicates a bad request was made. Takes a message or a message format with args like
  clojure.core/format."
  [& args]
  (apply throw-service-error :bad-request args))

(defn invalid-data
  "Indicates invalid data was passed in. Takes a message or a message format with args like
  clojure.core/format."
  [& args]
  (apply throw-service-error :invalid-data args))
