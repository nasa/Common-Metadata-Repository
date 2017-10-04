(ns cmr-edsc-stubs.data.fake-response
  "Functions for providing responses mimicking those of the CMR."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr-edsc-stubs.util :as util]
   [cmr.sample-data.core :as data-sources]))

(def ges-disc-airx3std-collection-id "C1238517344-GES_DISC")

(defn- gen-ids
  [provider-id prefix coll]
  (->> coll
       count
       inc
       (range 1)
       (map #(str prefix % "-" provider-id))))

(defn get-ges-disc-variables-map
  []
  (let [vars (data-sources/get-ges-disc-airx3std-ch4-variables [:json :edn])
        ids (gen-ids "GES_DISC" "V000" vars)]
    (zipmap ids vars)))

(defn get-ges-disc-services-map
  []
  (let [svcs [(data-sources/get-ges-disc-airx3std-opendap-service [:json :edn])]
        ids (gen-ids "GES_DISC" "S000" svcs)]
    (zipmap ids svcs)))

(defn make-variables-services-associations
  [vars svcs]
  (json/generate-string
    {:variables (keys vars)
     :services (keys svcs)}))

(defn get-umm-json-ges-disc-airx3std-collection
  []
  (let [metadata (data-sources/get-ges-disc-airx3std-collection-metadata :json)
        umm (data-sources/get-ges-disc-airx3std-collection :json)
        vars (get-ges-disc-variables-map)
        svcs (get-ges-disc-services-map)]
    (str
      "{\"hits\": 1,
        \"took\": 27,
        \"items\": [{"
        "\"meta\":" metadata ","
        "\"associations\":" (make-variables-services-associations vars svcs) ","
        "\"umm\":" umm
      "}]}")))

(defn handle-prototype-request
  [path-w-extension params headers query-string]
  (case path-w-extension
    "collections" (get-umm-json-ges-disc-airx3std-collection)
    "variables" "{}"))
