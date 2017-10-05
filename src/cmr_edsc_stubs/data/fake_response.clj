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

(defn make-metadata
  ([concept-type concept-id]
    (make-metadata
     "GES_DISC" (str (java.util.UUID/randomUUID)) concept-type concept-id))
  ([provider-id native-id concept-type concept-id]
    {:revision-id 1
     :deleted false
     :format "application/vnd.nasa.cmr.umm+json"
     :provider-id provider-id
     :user-id "cmr-edsc-stubs"
     :native-id native-id
     :concept-id concept-id
     :revision-date "2017-09-27T18:20:46Z"
     :concept-type concept-type}))

(defn make-collection-metadata
  [concept-id]
  (assoc (make-metadata "collection" concept-id)
         :has-variables true
         :has-transforms true
         :has-formats false))

(defn make-service-metadata
  [concept-id]
  (make-metadata "service" concept-id))

(defn make-variable-metadata
  [concept-id]
  (make-metadata "variable" concept-id))

(defn make-variables-services-associations
  [vars svcs]
  {:variables (keys vars)
   :services (keys svcs)})

(defn get-item-payload
  [[meta-data umm-data]]
  {:meta meta-data
   :umm umm-data})

(defn get-item-assocns-payload
  [[meta-data umm-data associations]]
  (assoc (get-item-payload [meta-data umm-data])
         :associations associations))

(defn get-result-payload
  [items]
  {:hits (count items)
   :took 42
   :items items})

(defn data->payload
  [item-payload-fn data]
  (->> data
       (map item-payload-fn)
       (get-result-payload)
       (json/generate-string)))

(defn get-concept-ids
  [params]
  (let [concept-id (:concept_id params)]
    (cond
      (nil? concept-id) []
      (coll? concept-id) concept-id
      :else [concept-id])))

(defn get-concept-id
  [params]
  (first (get-concept-ids params)))

(defn get-concept-ids-or-all
  [lookup params]
  (let [passed-concept-ids (get-concept-ids params)]
    (if (seq passed-concept-ids)
      passed-concept-ids
      (keys lookup))))

(defn get-umm-json-ges-disc-airx3std-collection
  []
  (let [umm-data (data-sources/get-ges-disc-airx3std-collection
                  :json [:json :edn])
        vars (get-ges-disc-variables-map)
        svcs (get-ges-disc-services-map)
        meta-data (make-collection-metadata (first (keys svcs)))
        assocns (make-variables-services-associations vars svcs)]
    (data->payload get-item-assocns-payload
                   [[meta-data umm-data assocns]])))

(defn- get-umm-json-ges-disc-airx3std-type
  [params lookup metadata-fn]
  (->> params
       (get-concept-ids-or-all lookup)
       (map (fn [x] [(metadata-fn x) (lookup x)]))
       (data->payload get-item-payload)))

(defn get-umm-json-ges-disc-airx3std-variables
  [params]
  (get-umm-json-ges-disc-airx3std-type params
                                       (get-ges-disc-variables-map)
                                       make-variable-metadata))

(defn get-umm-json-ges-disc-airx3std-services
  [params]
  (get-umm-json-ges-disc-airx3std-type params
                                       (get-ges-disc-services-map)
                                       make-service-metadata))

(defn handle-prototype-request
  [path-w-extension params headers query-string]
  (case path-w-extension
    "collections" (get-umm-json-ges-disc-airx3std-collection)
    "variables" (get-umm-json-ges-disc-airx3std-variables params)
    "services" (get-umm-json-ges-disc-airx3std-services params)))
