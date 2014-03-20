(ns ^{:doc "CMR Ingest integration tests"}
  cmr-system-int-test.concept-ingest-test
  (:require [clojure.test :refer :all]
            [cmr-system-int-test.ingest-util :as ingest]
            [cmr-system-int-test.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr-system-int-test.ingest-util :as util]))

;;; data related
(def base-concept-attribs
  {:short-name "SN-Sedac88" 
   :version "Ver88"  
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

(defn concept
  "Creates a sample concept."
  [provider-suffix-num]
  {:concept-type :collection
   :native-id "nativeId1"
   :provider-id (str "PROV" provider-suffix-num)
   :metadata (util/metadata-xml base-concept-attribs)
   :format "iso19115+xml"})

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept  
        ctx-part (str "providers" "/" provider-id  "/" "collections" "/" native-id )
        ingest-rest-url (str "http://" host ":" port "/" ctx-part)]
    ingest-rest-url))

;;; operation
(defn ingest-concept
  "Make a concept create / delete request on ingest.  Returns a map with
  status, concept-id, and revision-id"
  [method concept]
  (let [response (client/request
                   {:method (keyword method)
                    :url (construct-ingest-rest-url concept) 
                    :body  (io/string-input-stream (:metadata concept))
                    :body-encoding "UTF-8"
                    :content-type (:format concept)
                    :socket-timeout 2000  ;; in milliseconds
                    :conn-timeout 2000    ;; in milliseconds
                    :accept :json
                    :throw-exceptions true})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

(def provider-suffix-num-atom (atom (rand-int 1000000)))

;;; fixture related
(def ^:dynamic *save-concept-list* nil)
(def ^:dynamic *delete-concept-list* nil)
(def rand-start-num (atom (rand-int 1000000)))

(defn distinct-concept 
  "Generate a concept-type, provicer-id, native-id tuple"
  [token]
  (hash-map :concept-type :collection
            :provider-id (str "PROV" token)
            :native-id (str "nativeId" token)
            :metadata (util/metadata-xml base-concept-attribs)
            :format "iso19115+xml"))

(defn gen-distinct-concepts
  "Generate requested number of distinct concepts."
  [seed-val num-concepts]
 (map distinct-concept (range seed-val (+ seed-val num-concepts))))

(defn setup
  "set stage for creating P (save concepts) and Q (delete concepts ) new distinct concepts in mdb and index."
  [P Q]
  (alter-var-root #'*save-concept-list* 
                  (constantly (gen-distinct-concepts @rand-start-num P)))
  (alter-var-root #'*delete-concept-list* 
                  (constantly (gen-distinct-concepts (+ @rand-start-num P) Q)))
  (swap! rand-start-num + P Q))

;; teardown function behavior not correct ???
(defn teardown
  "tear down after the test - delete test created concepts"
  []
  (for [saved-concept *save-concept-list*]
    (ingest-concept :delete saved-concept)))
  

;;; tests
;;; ensure metadata and ingest apps are accessable on ports 3001 and 3002 resp; 
;;; add indexer app etc later to this list
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Ingest a new concept with no revision-id.
(deftest concept-ingest-test
  (let [{:keys [status revision-id]} (ingest-concept :put (first *save-concept-list*))]
    (is (and (= status 200) (= revision-id 0)))))

;; Ingest same concept N times and verify it is in metadata db with revision id value 'N - 1'.
(deftest repeat-same-concept-ingest-test
  (let [N 4
        expected-revision-id (- N 1)
        last-revision-id (last (repeatedly N
                                           #(let [ {:keys [revision-id]} (ingest-concept :put (second *save-concept-list*))]
                                              revision-id)))]
    (is (= expected-revision-id last-revision-id))))

;; Verify concept is deleted from mdb and index.
(deftest save-concept-for-future-delete-test
  (let [{:keys [status revision-id]} (ingest-concept :put (first *delete-concept-list*))]
    (is (and (= status 200) (= revision-id 0)))))

(deftest delete-concept-test
  (let [{:keys [status]} (ingest-concept :delete (first *delete-concept-list*))]
    (is (= status 200))))

(deftest save-n-delete-test
  (save-concept-for-future-delete-test)
  (delete-concept-test))

(defn ingest-test-fixture 
  [f]
  (setup 2 1)
  (try
    (f)
    (finally (teardown))))

(deftest test-suite
  (concept-ingest-test)
  (repeat-same-concept-ingest-test)
  (save-n-delete-test))

(defn test-ns-hook []
  (ingest-test-fixture test-suite))


(comment
  (test-ns *ns*))



