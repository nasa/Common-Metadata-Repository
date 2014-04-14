(ns cmr.common.services.errors
  "Contains functions that will throw errors that when caught in the API will return the
  correct error code."
  (:require [cmr.common.api.errors :as api-errors]))

(defn throw-service-error
  "Throws an instance of clojure.lang.ExceptionInfo that will contain a map with the type of
  error and a message. See http://stackoverflow.com/a/16159584."
  ([type msg]
   (throw (ex-info msg {:type type :errors [msg]})))
  ([type msg cause]
   (throw (ex-info msg {:type type :errors [msg]} cause))))

(defn throw-service-errors
  "Throws an instance of clojure.lang.ExceptionInfo that will contain a map with the type of
  error and errors. See http://stackoverflow.com/a/16159584."
  ([type errors]
   (throw (ex-info (first errors) {:type type :errors errors})))
  ([type errors cause]
   (throw (ex-info (first errors) {:type type :errors errors} cause))))

(defn internal-error!
  "Throws an Exception with the given message and error, if given, to indicate an internal error in the system."
  ([msg]
   (throw (Exception. msg)))
  ([msg cause]
   (throw (Exception. msg cause))))
