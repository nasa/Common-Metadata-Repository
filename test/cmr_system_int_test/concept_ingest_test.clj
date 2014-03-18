(ns ^{:doc "CMR Ingest integration tests"}
  cmr-system-int-test.concept-ingest-test
  (:require [clojure.test :refer :all]
            [cmr-system-int-test.ingest-util :as ingest]
            [cmr-system-int-test.index-util :as index]
            [cmr.common.log :as log :refer (debug info warn error)]
            [clj-http.client :as client]
            [cheshire.core :as cheshire]))

(defn concept
  "Creates a sample concept."
  [provider-suffix-num]
  {:concept-type :collection
   :native-id "nativeId1"
   :provider-id (str "PROV" provider-suffix-num)
   :metadata "xml here"
   :format "iso19115"})

(defn construct-ingest-rest-url
  "Construct ingest url based on concept."
  [concept]
  (let [host "localhost"
        port 3002
        {:keys [provider-id concept-type native-id ]} concept  
        ctx-part (str "providers" "/" provider-id  "/" "collections" "/" native-id )
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

;;; tests
;;; ensure metadata and ingest apps are accessable on ports 3001 and 3002 resp; 
;;; add indexer app etc later to this list
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest concept-ingest-test
  "Save a valid concept with no revision-id."
  (swap! provider-suffix-num-atom inc)
  (try
    (let [{:keys [status revision-id]} (save-concept (concept @provider-suffix-num-atom))]
      (is (and (= status 200) (= revision-id 0))))
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



