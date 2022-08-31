(ns cmr.system-int-test.search.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clojure.string :as string]
   [clojure.java.io :as jio]
   [cmr.common.config :as config]
   [cmr.common.generics :as gcfg]
   [cmr.common.util :as cutil]
   [clojure.test :refer :all]
   [cmr.ingest.api.generic-documents :as gdocs]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config])
  (:import
   [java.util UUID]))

(use-fixtures :each
  (join-fixtures
   [(ingest/reset-fixture
     {"provguid1" "PROV1"})]))

;; This is the sample record that comes with Grid
(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type provider-id native-id] (generic-request concept-type provider-id native-id nil :get))
  ([concept-type provider-id native-id document method]
  (-> {:method method
       :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
       :connection-manager (system/conn-mgr)
       :body (when document (json/generate-string document))
       :throw-exceptions false
       :headers {"Accept" "application/json"
                 transmit-config/token-header
                 (transmit-config/echo-system-token)}}
      (clj-http.client/request))))

(defn search-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type-ext params]
   (-> {:method :get
        :url (format "%s%s?%s" (url-helper/search-root) concept-type-ext params)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false
        :headers { transmit-config/token-header
                  (transmit-config/echo-system-token)}}
       (clj-http.client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest test-search-results
  "Test that a Generic can be searched and have the search results use XML."

  (let [native-id (format "Generic-Test-%s" (UUID/randomUUID))
        generic-requester (partial generic-request "grid" "PROV1" native-id)
        good-generic-requester (partial generic-requester grid-good)
        create-result (good-generic-requester :post)]
    (index/wait-until-indexed)
    (search-request "grid" "name=Grid-A7-v1")))


 ;   (testing "CREATE a document with a valid config set that includes grid - test that"
 ;     (let [expected {:Info {:native-id native-id
 ;                            :document-name "Grid-A7-v1"
 ;                            :provider-id "PROV1"
 ;                            :schema "grid"
 ;                            :format "application/vnd.nasa.cmr.umm+json;version=0.0.1"
 ;                            :mime-type "application/grid;version=0.0.1"
 ;                            :revision-id 1
 ;                            :user-id "ECHO_SYS"}
 ;                     :Metadata grid-good}
;
;            
;            create-response (:body create-result)
;
;            read-result (generic-requester)
;            read-response (:body read-result)
;
;            actual (json/parse-string read-response true)
;            concept-id (get-in actual [:Info :concept-id])
;            normalised (-> actual
;                           ;; these fields are complicated to test, do so another way
;                           (cutil/dissoc-in [:Info :concept-id])
;                           (cutil/dissoc-in [:Info :revision-date])
;;                           (cutil/dissoc-in [:Info :created-at]))
 ;           expected-pattern (re-pattern "\\{\"concept-id\":\"GRD[0-9]+-PROV1\",\"revision-id\":1\\}")]
;
;        ;; test that the create was a success
;        (is (= 201 (:status create-result)) "The HTTP status code from create is not correct")
;        (is (some? (re-matches expected-pattern create-response))
;            "Create returned content when it should not have")
;
;        ;; test that the created document can be read back and was stored correctly
;        (is (= 200 (:status read-result)) "The HTTP status code from read is not correct")
;        (is (= "application/json;charset=utf-8" (get-in read-result [:headers "Content-Type"]))
;            "The content type is not correct)")
;
;        ;; test the three complicated fields
;        (is (some? (re-matches #"GRD[0-9]+-PROV1" concept-id)) "A concept id was not returned")
;        (is (some? (get-in actual [:Info :revision-date])) "Response did not have a revision date")
;        (is (some? (get-in actual [:Info :created-at])) "Response did not have a create date")
;
;        ;; Test the content in sections to make it easy to see where a problem may be
;        (is (= (:Info expected) (:Info normalised)) "Info section does not match")
;        (is (= (:Metadata expected) (:Metadata normalised)) "Metadata section did not match")))
;