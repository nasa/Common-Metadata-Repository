(ns cmr.mock-echo.api.api-helpers
  (:require [cmr.common.mime-types :as mt]
            [cmr.common.services.errors :as svc-errors]
            [cheshire.core :as json]))

(defmulti prepare-body type)

(defmethod prepare-body String
  [body]
  body)

(defmethod prepare-body :default
  [body]
  (json/encode body))

(defn status-ok
  [body]
  {:status 200
   :headers {"Content-type" mt/json}
   :body (prepare-body body)})

(defn status-bad-request
  [body]
  {:status 400
   :headers {"Content-type" mt/json}
   :body (prepare-body body)})

(defn status-gateway-timeout
  "Generate a response for the BODY with the specified OPTS format."
  ([body]
   (status-gateway-timeout body {:format mt/html}))
  ([body opts]
   {:status 504
    :headers {"Content-type" (:format opts)}
    :body (prepare-body body)}))

(defn status-created
  [body]
  {:status 201
   :headers {"Content-type" mt/json}
   :body (prepare-body body)})

(defn require-sys-admin-token
  [headers]
  (when-not (get headers "authorization")
    (svc-errors/throw-service-error
      :unauthorized
      "User does not have requested access to [TOKEN].")))
