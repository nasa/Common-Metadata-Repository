(ns cmr.opendap.ous.core
  (:require
    [cmr.opendap.ous.v1 :as v1]
    [cmr.opendap.ous.v2-1 :as v2-1]
    [taoensso.timbre :as log]))

(defn get-opendap-urls
  [component api-version user-token raw-params]
  (log/trace "Got API version:" api-version)
  (case (keyword api-version)
    :v2.1 (v2-1/get-opendap-urls component user-token raw-params)
    (v1/get-opendap-urls component user-token raw-params)))
