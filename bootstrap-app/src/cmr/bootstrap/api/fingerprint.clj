(ns cmr.bootstrap.api.fingerprint
  "Defines the fingerprint functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]))

(defn fingerprint-variables
  "Calculate the fingerprint of the variables matching the given params.
  For each matched variable, if the calculated fingerprint value is different from the
  existing fingerprint, create a new revision of the variable concept with the new fingerprint;
  otherwise, do nothing."
  [context body params]
  (let [dispatcher (api-util/get-dispatcher context params :fingerprint-variables)]
    (service/fingerprint-variables context dispatcher body)
    {:status 200}))

(defn fingerprint-by-id
  "Calculate the fingerprint of the given variable. If the calculated fingerprint value
  is different from the existing fingerprint, create a new revision of the variable concept
  with the new fingerprint; otherwise, do nothing."
  [context concept-id]
  (let [dispatcher (api-util/get-dispatcher context {} :fingerprint-by-id)]
    (service/fingerprint-by-id context dispatcher concept-id)
    {:status 200}))
