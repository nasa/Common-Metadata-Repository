(ns cmr.system-int-test.utils.service-util
  "This contains utilities for testing services."
  (:require
   [cmr.common.mime-types :as mt]
   [cmr.system-int-test.data2.umm-spec-service :as data-umm-s]
   [cmr.umm-spec.versioning :as versioning]))

(def schema-version versioning/current-service-version)
(def content-type "application/vnd.nasa.cmr.umm+json")

(defn make-service-concept
  ([]
    (make-service-concept {}))
  ([metadata-attrs]
    (make-service-concept metadata-attrs {}))
  ([metadata-attrs attrs]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-s/service-concept)
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs)))
  ([metadata-attrs attrs idx]
    (-> (merge {:provider-id "PROV1"} metadata-attrs)
        (data-umm-s/service-concept idx)
        (assoc :format (mt/with-version content-type schema-version))
        (merge attrs))))
