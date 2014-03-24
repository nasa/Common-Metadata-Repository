(ns ^{:doc "CMR Ingest integration tests"}
  cmr.system-int-test.concept-ingest-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.ingest-util :as ingest]
            [cmr.system-int-test.index-util :as index]
            [ring.util.io :as io]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]
            [cmr.system-int-test.ingest-util :as util]))

(def base-concept-attribs
  {:short-name "SN-Sedac88"
   :version "Ver88"
   :long-name "LongName Sedac88"
   :dataset-id "LarcDatasetId88"})

;; Each test ingests and deletes this concept.
(defn concept
  "Creates a sample concept."
  [provider-num]
  {:concept-type :collection
   :native-id "nativeId1"
   :provider-id (str "PROV88" provider-num)
   :metadata (util/metadata-xml base-concept-attribs)
   :format "echo10+xml"})

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept
        ctx-part (str "providers" "/" provider-id  "/" "collections" "/" native-id )
        ingest-rest-url (str "http://" host ":" port "/" ctx-part)]
    ingest-rest-url))

;;; operations
(defn ingest-concept
  "Ingest a concept and return a map with status, concept-id, and revision-id"
  [concept]
  (let [response (client/request
                   {:method :put
                    :url (construct-ingest-rest-url concept)
                    :body  (:metadata concept) ;; (io/string-input-stream (:metadata concept))
                    :content-type (:format concept)
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

(defn delete-concept
  "Delete a given concept."
  [concept]
  (let [response (client/request
                   {:method :delete
                    :url (construct-ingest-rest-url concept)
                    :accept :json
                    :throw-exceptions false})
        status (:status response)
        body (cheshire/parse-string (:body response))
        concept-id (get body "concept-id")
        revision-id (get body "revision-id")]
    {:status status :concept-id concept-id :revision-id revision-id :response response}))

;;; tests
;;; ensure metadata and ingest apps are accessable on ports 3001 and 3002 resp;
;;; add indexer app etc later to this list
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Ingest a new concept with no revision-id.
(deftest concept-ingest-test
  (let [provider-num (rand-int 100000)
        {:keys [status revision-id]} (ingest-concept (concept provider-num))]
    (delete-concept (concept provider-num))
    (is (and (= status 200) (= revision-id 0)))))

;; Ingest same concept N times and verify it is in metadata db with revision id value 'N - 1'.
(deftest repeat-same-concept-ingest-test
  (let [n 4
        provider-num (rand-int 100000)
        expected-revision-id (- n 1)
        last-revision-id (last (repeatedly n
                                           #(:revision-id (ingest-concept (concept provider-num)))))]
    (repeatedly n #(delete-concept (concept provider-num)))
    (is (= expected-revision-id last-revision-id))))

;; Verify concept ingest and delete are successful.
(deftest delete-concept-test
  (let [provider-num (rand-int 100000)
        {:keys [revision-id]} (ingest-concept (concept provider-num))
        {:keys [status]} (delete-concept (concept provider-num))]
    (is (= status 200))))




