(ns cmr.mock-echo.api.api-helpers
  (:require [cmr.common.services.errors :as svc-errors]
            [cheshire.core :as json]))

(defmulti prepare-body
  (fn [body]
    (type body)))

(defmethod prepare-body String
  [body]
  body)

(defmethod prepare-body :default
  [body]
  (json/encode body))

(defn status-ok
  [body]
  {:status 200
   :headers {"Content-type" "application/json"}
   :body (prepare-body body)})

(defn require-sys-admin-token
  [headers]
  (when-not (get headers "echo-token")
    (svc-errors/throw-service-error :unauthorized "User does not have requested access to [TOKEN].")))