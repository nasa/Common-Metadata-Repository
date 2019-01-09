(ns cmr-edsc-stubs.data.fake-response
  "Functions for providing responses mimicking those of the CMR."
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [cmr-edsc-stubs.util :as util]
   [cmr.sample-data.core :as data-sources]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Get Data from Sources   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(defn get-all-metadata
  []
  (merge (get-ges-disc-variables-map)
         (get-ges-disc-services-map)))

(defn make-variables-services-associations
  [vars svcs]
  {:variables (keys vars)
   :services (keys svcs)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   UMM Data Structure Generators Fake Responses   ;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn make-umm-metadata
  ([concept-type concept-id]
    (make-umm-metadata
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

(defn make-umm-collection-metadata
  [concept-id]
  (assoc (make-umm-metadata "collection" concept-id)
         :has-variables true
         :has-transforms true
         :has-formats false))

(defn make-umm-service-metadata
  [concept-id]
  (make-umm-metadata "service" concept-id))

(defn make-umm-variable-metadata
  [concept-id]
  (make-umm-metadata "variable" concept-id))

(defn get-umm-item-payload
  [[meta-data umm-data]]
  {:meta meta-data
   :umm umm-data})

(defn get-umm-item-assocns-payload
  [[meta-data umm-data associations]]
  (assoc (get-umm-item-payload [meta-data umm-data])
         :associations associations))

(defn get-umm-json-result-payload
  [items]
  {:hits (count items)
   :took 42
   :items items})

(defn data->umm-json-payload
  [item-payload-fn data]
  (->> data
       (map item-payload-fn)
       (get-umm-json-result-payload)
       (json/generate-string)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   UMM Data for Fake Responses   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-umm-json-ges-disc-airx3std-collection
  []
  (let [umm-data (data-sources/get-ges-disc-airx3std-collection
                  :umm-json [:json :edn])
        vars (get-ges-disc-variables-map)
        svcs (get-ges-disc-services-map)
        meta-data (make-umm-collection-metadata (first (keys svcs)))
        assocns (make-variables-services-associations vars svcs)]
    (data->umm-json-payload
      get-umm-item-assocns-payload
      [[meta-data umm-data assocns]])))

(defn- get-umm-json-ges-disc-airx3std-type
  [params lookup metadata-fn]
  (->> params
       (get-concept-ids-or-all lookup)
       (map (fn [x] [(metadata-fn x) (lookup x)]))
       (data->umm-json-payload get-umm-item-payload)))

(defn get-umm-json-ges-disc-airx3std-variables
  [params]
  (get-umm-json-ges-disc-airx3std-type params
                                       (get-ges-disc-variables-map)
                                       make-umm-variable-metadata))

(defn get-umm-json-ges-disc-airx3std-services
  [params]
  (get-umm-json-ges-disc-airx3std-type params
                                       (get-ges-disc-services-map)
                                       make-umm-service-metadata))
(defn get-umm-json-concept
  [concept-id]
  (json/generate-string
    ((get-all-metadata) concept-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   JSON Data Structure Generators Fake Responses   ;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-json-item-payload
  [json-data]
  (merge json-data
         {:has_variables true
          :has_transforms true
          :has_formats false}))

(defn get-json-item-assocns-payload
  [[json-data associations]]
  (assoc (get-json-item-payload json-data) :associations associations))

(defn get-json-result-payload
  [items]
  {:feed
    {:updated "2017-10-10T23:19:19.360Z",
     :id "stubbed-data/search/collections.json",
     :title "ECHO dataset metadata",
     :entry items}})

(defn data->json-payload
  [item-payload-fn data]
  (->> data
       (map item-payload-fn)
       (get-json-result-payload)
       (json/generate-string)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   JSON Data for Fake Responses   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-json-ges-disc-airx3std-collection
  []
  (let [json-data (data-sources/get-ges-disc-airx3std-collection
                   :json [:json :edn])
        vars (get-ges-disc-variables-map)
        svcs (get-ges-disc-services-map)
        assocns (make-variables-services-associations vars svcs)]
    (data->json-payload
      get-json-item-assocns-payload
      [[json-data assocns]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   UMM Data for Fake Responses   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-collections
  [path-w-extension params headers]
  (if (string/includes? (str (headers "accept") path-w-extension) "umm")
    (get-umm-json-ges-disc-airx3std-collection)
    (get-json-ges-disc-airx3std-collection)))

(defn handle-prototype-request
  ([path-w-extension params headers]
    (handle-prototype-request path-w-extension params headers ""))
  ([path-w-extension params headers query-string]
    (cond
      (string/starts-with? path-w-extension "collections")
        (get-collections path-w-extension params headers)
      (string/starts-with? path-w-extension "variables")
        (get-umm-json-ges-disc-airx3std-variables params)
      (string/starts-with? path-w-extension "services")
        (get-umm-json-ges-disc-airx3std-services params)
      :else (get-umm-json-concept path-w-extension))))
