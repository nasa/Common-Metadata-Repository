(ns cmr.ingest.test.mdb
  (:require [clojure.test :refer :all]
            [cmr.common.log :as log :refer (debug info warn error)]
            [cmr.ingest.data.mdb :as mdb-fns]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))


(defn concept
  "Creates a sample concept."
  [provider-suffix-num]
  {:concept-type :collections
   :native-id "nativeId1"
   :provider-id (str "PROV" provider-suffix-num)
   :metadata "xml here"
   :format "iso19115"})

(def mdb-url
  (str "http://localhost:3001/concepts"))

(def http-request-val
  {:method :post
   :url "http://localhost:3001/concepts"
   :body "{\"concept-type\":\"collections\"}"
   :body-encoding "UTF-8"
   :content-type :json
   :socket-timeout 2000  ;; in milliseconds
   :conn-timeout 2000    ;; in milliseconds
   :accept :json})

(def concept-id-val
  (let [{:keys [concept-type provider-id native-id]} (concept 77)]
    (str provider-id "-" (name concept-type) "-"  native-id)))

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept  
        ctx-part (str "providers" "/" provider-id  "/" (name concept-type) "/" native-id )
        ingest-rest-url (str "http://" host ":" port "/" ctx-part)]
    ingest-rest-url))

(defn save-concept
  "Make a put request to ingest a concept without JSON encoding the concept.  Returns a map with
  status, revision-id, and a list of error messages"
  [concept]
  (let [coerced-json-str (cheshire/generate-string concept)
        response (client/request
                   {:method :put
                    :url (construct-ingest-rest-url concept) 
                    :body  coerced-json-str
                    :body-encoding "UTF-8"
                    :content-type :json
                    :socket-timeout 2000  ;; in milliseconds
                    :conn-timeout 2000    ;; in milliseconds
                    :accept :json
                    :throw-exceptions true})
        status (:status response)
        body (cheshire/parse-string (:body response))
        revision-id (get body "revision-id")
        error-messages (get body "errors")]
    {:status status :revision-id revision-id :error-messages error-messages :response response}))

(def provider-suffix-num-atom (atom (rand-int 1000000)))

;;; int tests
;;; ensure metadata and ingest apps are accessable on ports 3001 and 3002 resp; 
;;; add indexer app etc later to this list
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest concept-ingest-test
  "Save a valid concept with no revision-id."
  (swap! provider-suffix-num-atom inc)
  (try
    (let [{:keys [status revision-id]} (save-concept (concept @provider-suffix-num-atom))]
      (info status)
      (info revision-id)
      (is (and (= status 201) (= revision-id 0))))
    (catch Exception e
      (error "Exception occurred while executing concept-ingest-test: " (.getMessage e))
      (.printStackTrace e)
      (is false))))

(deftest repeat-same-concept-ingest-test
  "Ingest same concept N times and verify it is in metadata db with revision id value 'N - 1'."
  (swap! provider-suffix-num-atom inc)
  (try
    (let [N 4
          expected-revision-id (- N 1)
          last-revision-id (last (repeatedly N
                                             #(let [ {:keys [revision-id]} (save-concept (concept @provider-suffix-num-atom))]
                                                revision-id)))]
      (is (= expected-revision-id last-revision-id)))
    (catch Exception e
      (error "Exception occurred while executing repeat-same-concept-ingest-test: " (.getMessage e))
      (.printStackTrace e)
      (is false))))

;;; unit tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#_(deftest test-build-http-request-fn
  (let [ op "post"
        url ( :mdb-url (mdb-fns/metadata-db-config ))
        json-str (cheshire/generate-string {:concept-type :collections})
        req (mdb-fns/build-http-request-fn op url json-str)]
    (is (= req http-request-val))))

#_(deftest test-defrecord-get-concept-id
  (let 
    [mdb-instance (cmr.ingest.data.mdb.Metadata-DB. {:config (mdb-fns/metadata-db-config)})
     {:keys [concept-type provider-id native-id]} (concept 77)
     concept-id (mdb-fns/.get-concept-id   mdb-instance concept-type provider-id native-id)]
    (is (= concept-id concept-id-val))))


