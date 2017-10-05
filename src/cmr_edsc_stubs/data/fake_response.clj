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
    (->> [[meta-data umm-data assocns]]
         (map get-item-assocns-payload)
         (get-result-payload)
         (json/generate-string))))

(defn get-umm-json-ges-disc-airx3std-variables
  [params]
  (let [vars (get-ges-disc-variables-map)]
    (->> params
         (get-concept-ids-or-all vars)
         (map (fn [x] [(make-variable-metadata x) (vars x)]))
         (map get-item-payload)
         (get-result-payload)
         (json/generate-string))))

(defn get-umm-json-ges-disc-airx3std-variable
  [params]
  (let [vars (get-ges-disc-variables-map)
        concept-id (get-concept-id params)
        umm-data (vars concept-id)
        meta-data (make-variable-metadata concept-id)]
    (->> [[meta-data umm-data]]
         (map get-item-payload)
         (get-result-payload)
         (json/generate-string))))

(defn get-umm-json-ges-disc-airx3std-service
  [params]
  (let [svcs (get-ges-disc-services-map)
        concept-id (get-concept-id params)
        umm-data (svcs concept-id)
        meta-data (make-service-metadata concept-id)]
    (->> [[meta-data umm-data]]
         (map get-item-payload)
         (get-result-payload)
         (json/generate-string))))

(defn handle-prototype-request
  [path-w-extension params headers query-string]
  (case path-w-extension
    "collections" (get-umm-json-ges-disc-airx3std-collection)
    "variables" (get-umm-json-ges-disc-airx3std-variables params)
    "services" (get-umm-json-ges-disc-airx3std-service params)))
