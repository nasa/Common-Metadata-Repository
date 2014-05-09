(ns cmr.indexer.data.index-set
  (:require [cmr.common.lifecycle :as lifecycle]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.common.services.errors :as errors]
            [cmr.indexer.data.cache :as cache]
            [cmr.system-trace.core :refer [deftracefn]]))

;;; index-set app enpoint
(def index-set-app-port 3005)

(def index-set-root-url
  (format "%s:%s"  "http://localhost" index-set-app-port))

;; url applicable to create, get and delete index-sets
(def index-set-url
  (format "%s/%s" index-set-root-url "index-sets"))

;; url applicable to create, get and delete index-sets
(def index-set-es-cfg-url
  (format "%s/%s" index-set-root-url "elastic-config"))

;; reset indices for dev purposes
(def index-set-reset-url
  (format "%s/%s" index-set-root-url "reset"))

(def collection-setting {:index
                         {:number_of_shards 2,
                          :number_of_replicas 1,
                          :refresh_interval "1s"}})

;; TODO verify that all these options are necessary
(def string-field-mapping
  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "no"})

(def date-field-mapping
  {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"})

(def float-field-mapping
  {:type "float"})

(def int-field-mapping
  {:type "integer"})

(def bool-field-mapping
  {:type "boolean"})

(defn stored
  "modifies a mapping to indicate that it should be stored"
  [field-mapping]
  (assoc field-mapping :store "yes"))

(def attributes-field-mapping
  "Defines mappings for attributes."
  {:type "nested"
   :dynamic "strict"
   :properties
   {:name string-field-mapping
    :string-value string-field-mapping
    :float-value float-field-mapping
    :int-value int-field-mapping
    :datetime-value date-field-mapping
    :time-value date-field-mapping
    :date-value date-field-mapping}})

(def orbit-calculated-spatial-domain-mapping
  {:type "nested"
   :dynamic "strict"
   :properties {:orbit-number float-field-mapping
                :start-orbit-number float-field-mapping
                :stop-orbit-number float-field-mapping
                :equator-crossing-longitude float-field-mapping
                :equator-crossing-date-time date-field-mapping}})

(def collection-mapping
  {:collection {:dynamic "strict",
                :_source {:enabled false},
                :_all {:enabled false},
                :_id   {:path "concept-id"},
                :properties {:concept-id            (stored string-field-mapping)
                             :entry-id           (stored string-field-mapping)
                             :entry-id.lowercase string-field-mapping
                             :entry-title           (stored string-field-mapping)
                             :entry-title.lowercase string-field-mapping
                             :provider-id           (stored string-field-mapping)
                             :provider-id.lowercase string-field-mapping
                             :short-name            (stored string-field-mapping)
                             :short-name.lowercase  string-field-mapping
                             :version-id            (stored string-field-mapping)
                             :version-id.lowercase  string-field-mapping
                             :revision-date         date-field-mapping
                             :processing-level-id    string-field-mapping
                             :processing-level-id.lowercase  string-field-mapping
                             :start-date            date-field-mapping
                             :end-date              date-field-mapping
                             :platform-sn           string-field-mapping
                             :platform-sn.lowercase string-field-mapping
                             :instrument-sn           string-field-mapping
                             :instrument-sn.lowercase string-field-mapping
                             :sensor-sn             string-field-mapping
                             :sensor-sn.lowercase   string-field-mapping
                             :project-sn            string-field-mapping
                             :project-sn.lowercase  string-field-mapping
                             :archive-center        string-field-mapping
                             :archive-center.lowercase string-field-mapping
                             :two-d-coord-name string-field-mapping
                             :two-d-coord-name.lowercase string-field-mapping
                             :attributes attributes-field-mapping}}})

(def granule-setting {:index {:number_of_shards 2,
                              :number_of_replicas 1,
                              :refresh_interval "1s"}})

(def granule-mapping
  {:granule
   {:dynamic "strict",
    :_source { "enabled" false},
    :_all {"enabled" false},
    :_id  {:path "concept-id"},
    :properties {:concept-id            (stored string-field-mapping)
                 :collection-concept-id (stored string-field-mapping)

                 ;; Collection fields added strictly for sorting granule results
                 :entry-title.lowercase string-field-mapping
                 :short-name.lowercase  string-field-mapping
                 :version-id.lowercase  string-field-mapping

                 :provider-id           (stored string-field-mapping)
                 :provider-id.lowercase string-field-mapping

                 :granule-ur            (stored string-field-mapping)
                 :granule-ur.lowercase  string-field-mapping
                 :producer-gran-id string-field-mapping
                 :producer-gran-id.lowercase string-field-mapping

                 ;; We need to sort by a combination of producer granule and granule ur
                 ;; It should use producer granule id if present otherwise the granule ur is used
                 ;; The producer granule id will be put in this field if present otherwise it
                 ;; will default to granule-ur. This avoids the solution Catalog REST uses which is
                 ;; to use a sort script which is (most likely) much slower.
                 :readable-granule-name-sort string-field-mapping

                 :platform-sn           string-field-mapping
                 :platform-sn.lowercase string-field-mapping
                 :instrument-sn         string-field-mapping
                 :instrument-sn.lowercase string-field-mapping
                 :sensor-sn             string-field-mapping
                 :sensor-sn.lowercase   string-field-mapping
                 :start-date date-field-mapping
                 :end-date date-field-mapping
                 :size float-field-mapping
                 :cloud-cover float-field-mapping
                 :orbit-calculated-spatial-domains orbit-calculated-spatial-domain-mapping
                 :project-refs string-field-mapping
                 :project-refs.lowercase string-field-mapping
                 :revision-date         date-field-mapping
                 :downloadable bool-field-mapping
                 :attributes attributes-field-mapping}}})

(def index-set
  {:index-set {:name "cmr-base-index-set"
               :id 1
               :create-reason "indexer app requires this index set"
               :collection {:index-names ["collections"]
                            :settings collection-setting
                            :mapping collection-mapping}
               :granule {:index-names ["granules"]
                         :settings granule-setting
                         :mapping granule-mapping}}})


(defn get-index-set
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" index-set-url (str id))
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/decode (:body response) true)]
    (case status
      404 nil
      200 body
      (errors/internal-error! (format "Unexpected error fetching index-set with id: %s,
                                      Index set app reported status: %s, error: %s"
                                      id status (pr-str (flatten (:errors body))))))))

(defn fetch-elastic-config
  "Submit a request to index-set app to fetch an index-set assoc with an id"
  []
  (let [response (client/request
                   {:method :get
                    :url index-set-es-cfg-url
                    :accept :json})]
    (cheshire/decode (:body response) true)))


(defn reset
  "Reset configured elastic indexes"
  []
  (client/request
    {:method :post
     :url index-set-reset-url
     :content-type :json
     :accept :json}))

(defn create
  "Submit a request to create index-set"
  [index-set]
  (let [response (client/request
                   {:method :post
                    :url index-set-url
                    :body (cheshire/generate-string index-set)
                    :content-type :json
                    :accept :json
                    :throw-exceptions false})
        status (:status response)]
    (when-not (= 201 status)
      (errors/internal-error! (format "Failed to create index-set: %s, errors: %s"
                                      (cheshire/generate-string index-set)
                                      (:body response))))))

(defn fetch-concept-type-index-names
  "Fetch index names for each concept type from index-set app"
  ([]
   (let [index-set-id (get-in index-set [:index-set :id])]
     (fetch-concept-type-index-names index-set-id)))
  ([index-set-id]
   (let [fetched-index-set (get-index-set index-set-id)]
     (into {} (map (fn [[k v]] [k (first (vals v))])
                   (get-in fetched-index-set [:index-set :concepts]))))))


(defn fetch-concept-mapping-types
  "Fetch mapping types for each concept type from index-set app"
  ([]
   (let [index-set-id (get-in index-set [:index-set :id])]
     (fetch-concept-mapping-types index-set-id)))
  ([index-set-id]
   (let [fetched-index-set (get-index-set index-set-id)]
     {:collection (name (first (keys (get-in fetched-index-set [:index-set :collection :mapping]))))
      :granule (name (first (keys (get-in fetched-index-set [:index-set :granule :mapping]))))})))

(defn get-elastic-config
  "Fetch elastic config from the cache."
  [context]
  (let [cache-atom (-> context :system :cache)]
    (cache/cache-lookup cache-atom :elastic-config #(fetch-elastic-config))))

(defn get-concept-type-index-names
  "Fetch index names associated with concepts."
  [context]
  (let [cache-atom (-> context :system :cache)]
    (cache/cache-lookup cache-atom :concept-indices #(fetch-concept-type-index-names))))

(defn get-concept-mapping-types
  "Fetch mapping types associated with concepts."
  [context]
  (let [cache-atom (-> context :system :cache)]
    (cache/cache-lookup cache-atom :concept-mapping-types #(fetch-concept-mapping-types))))


