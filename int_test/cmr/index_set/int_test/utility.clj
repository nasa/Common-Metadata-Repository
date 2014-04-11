(ns cmr.index-set.int-test.utility
  "Contains various utitiltiy methods to support integeration tests."
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [clojure.data.codec.base64 :as b64]
            [cheshire.core :as cheshire]))


;;; index-set app enpoint
(def port 3005)

(def index-set-root-url
  (format "%s:%s"  "http://localhost" port))

;; url applicable to create, get and delete index-set
(def index-set-url
  (format "%s/%s" index-set-root-url "index-sets"))

(def reset-url
  (format "%s/%s" index-set-root-url "reset"))

(def cmr-concepts [:collection :granule])

(def config-file
  (io/resource "config/elasticsearch_config.json"))

;;; test data
;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def sample-index-set
  {:index-set {:name "cmr-base-index-set"
               :id 3
               :create-reason "include message about reasons for creating this index set"
               :collection {:index-names ["C4-collections", "c6_Collections"]
                            :settings {:index {:number_of_shards 1,
                                               :number_of_replicas 0,
                                               :refresh_interval "20s"}}
                            :mapping {:collection {:dynamic "strict",
                                                   :_source {:enabled false},
                                                   :_all {:enabled false},
                                                   :_id   {:path "concept-id"},
                                                   :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                                :entry-title {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}
               :granule {:index-names ["G2-PROV1", "G4-Prov3", "g5_prov5"]
                         :settings {:index {:number_of_shards 1,
                                            :number_of_replicas 0,
                                            :refresh_interval "10s"}}
                         :mapping {:granule {:dynamic "strict",
                                             :_source { "enabled" false},
                                             :_all {"enabled" false},
                                             :_id  {:path "concept-id"},
                                             :properties {:concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                          :collection-concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}}})

(def invalid-sample-index-set
  {:index-set {:name "cmr-base-index-set"
               :id 7
               :create-reason "include message about reasons for creating this index set"
               :collection {:index-names ["C4-collections", "c6_Collections"]
                            ; :settings {:index {:number_of_shards 1,
                            ;                 :number_of_replicas 0,
                            ;                :refresh_interval "20s"}}
                            :mapping {:collection {:dynamic "strict",
                                                   :_source {:enabled false},
                                                   :_all {:enabled false},
                                                   :_id   {:path "concept-id"},
                                                   :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                                :entry-title {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}
               :granule {:index-names ["G2-PROV1", "G4-Prov3", "g5_prov5"]
                         :settings {:index {:number_of_shards 1,
                                            :number_of_replicas 0,
                                            :refresh_interval "10s"}}
                         :mapping {:granule {:dynamic "strict",
                                             :_source { "enabled" false},
                                             :_all {"enabled" false},
                                             :_id  {:path "concept-id"},
                                             :properties {:concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                          :collection-concept-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}}})

(def index-set-w-invalid-idx-prop
  {:index-set {:name "cmr-base-index-set"
               :id 7
               :create-reason "include message about reasons for creating this index set"
               :collection {:index-names ["C4-collections", "c6_Collections"]
                            ; :settings {:index {:number_of_shards 1,
                            ;                 :number_of_replicas 0,
                            ;                :refresh_interval "20s"}}
                            :mapping {:collection {:dynamic "strict",
                                                   :_source {:enabled false},
                                                   :_all {:enabled false},
                                                   :_id   {:path "concept-id"},
                                                   :properties {:concept-id  {:type "XXX" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"},
                                                                :entry-title {:type "YYY" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}}}}}}})


;;; utility methods
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; app and int-tests are sharing same config resource which is not correct but using
;; it anyhow for convenience purposes
(defn es-config
  "Return the configuration for elasticsearch"
  []
  (let [{:strs [host port password]} (cheshire/decode (slurp config-file))]
    {:host host
     :port port
     :admin-token (str "Basic " (b64/encode (.getBytes password)))}))

(def elastic_root (format "http://%s:%s" (:host (es-config)) (:port (es-config))))

(defn elastic-flush-url
  []
  (str elastic_root "/_flush"))

(defn flush-elastic
  []
  (client/post (elastic-flush-url)))

(defn gen-valid-index-name
  "Join parts, lowercase letters and change '-' to '_'."
  [prefix-id suffix]
  (str/lower-case (str/replace (format "%s_%s" prefix-id suffix) #"-" "_")))

(defn get-index-names
  "Given a index set build list of index names."
  [idx-set]
  (let [prefix-id (-> idx-set :index-set :id)]
    (for [concept cmr-concepts
          suffix-index-name (-> idx-set :index-set concept :index-names)]
      (gen-valid-index-name prefix-id suffix-index-name))))

(defn  submit-create-index-set-req
  "submit a request to index-set app to create indices"
  [idx-set]
  (let [response (client/request
                   {:method :post
                    :url index-set-url
                    :body (cheshire.core/generate-string idx-set)
                    :content-type :json
                    ;; :headers {}
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    ; (println "int test submit-create-index-set-req - " response)
    {:status status :errors-str errors-str :response response}))

(defn  submit-delete-index-set-req
  "submit a request to index-set app to delete index-set"
  [id]
  (let [response (client/request
                   {:method :delete
                    :url (format "%s/%s" index-set-url id)
                    ;; :headers {}
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    ; (println "int test submit-delete-index-set-req - " response)
    {:status status :errors-str errors-str :response response}))

(defn  get-index-set
  "submit a request to index-set app to fetch an index-set assoc with an id"
  [id]
  (let [response (client/request
                   {:method :get
                    :url (format "%s/%s" index-set-url id)
                    ;; :headers {}
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    ; (println "int test get-index-set - " response)
    {:status status :errors-str errors-str :response response}))

(defn get-index-sets
  "submit a request to index-set app to fetch all index-sets"
  []
  (let [response (client/request
                   {:method :get
                    :url index-set-url
                    ;; :headers {}
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        errors-str (cheshire/generate-string (flatten (get body "errors")))]
    ; (println "int test get-index-sets - " response)
    {:status status :errors-str errors-str :response response}))

(defn reset
  "test deletion of indices and index-sets"
  []
  (let [result (client/request
                 {:method :post
                  :url (format "%s/%s" index-set-root-url "reset")
                  ;; :headers {}
                  :accept :json
                  :throw-exceptions false})
        status (:status result)
        {:keys [status errors-str response]} result]
    ; (println "int test reset - " result)
    {:status status :errors-str errors-str :response response}))

(defn list-es-indices
  "List indices present in 'get index-sets' response"
  [index-sets]
  (apply concat
         (for [concept cmr-concepts idx-set index-sets]
           (vals (get-in idx-set [:concepts concept])))))

;;; comment stuff
;;;;;;;;;;;;;;;;;;;;;;;;;;
(comment
  (submit-create-index-set-req sample-index-set)
  (submit-create-index-set-req index-set-w-invalid-idx-prop)
  )

