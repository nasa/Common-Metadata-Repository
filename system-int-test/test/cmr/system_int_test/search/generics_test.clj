(ns cmr.system-int-test.search.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.generic-util :as gen-util])
  (:import
   [java.util UUID]))

(defn grant-all-generic-permission-fixture
  "A test fixture that grants all users the ability to create and modify generic documents."
  [f]
  (echo-util/grant-system-ingest-management (system/context) [:read :update] [:read :update])
  (f))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1"})
                                    grant-all-generic-permission-fixture]))

(defn search-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type-ext params]
   (-> {:method :get
        :url (format "%s%s?%s" (url-helper/search-root) concept-type-ext params)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false}
       (client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Test that a Generic can be searched and have the search results use XML.
(deftest test-search-results

  (let [native-id (format "Generic-Test-%s" (UUID/randomUUID))
        generic-requester (partial gen-util/generic-request nil "PROV1" native-id "grid")
        good-generic-requester (partial generic-requester gen-util/grid-good)
        post-results (good-generic-requester :post)
        body (json/parse-string (:body post-results) true)
        concept-id (:concept-id body)
        revision-id (:revision-id body)]
    (index/wait-until-indexed)

    (testing "Check that test the document ingested before going forward with tests"
      (is (= 201 (:status post-results))) "failed to ingest test record")

    (testing "Test that generics can use XML search results."
      (let [results (search-request "grids" "name=Grid-A7-v1")
            status (:status results)
            body (:body results)]
        (is (string/includes? body "<name>Grid-A7-v1</name>") "record not found")
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics can use JSON search results."
      (let [results (search-request "grids.json" "name=Grid-A7-v1")
            status (:status results)
            body (json/parse-string (:body results) true)]
        (is (some? (:concept_id (first (:items body)))) "no concept id")
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics can use UMM_JSON search results."
      (let [results (search-request "grids.umm_json" "name=Grid-A7-v1")
            status (:status results)
            body (json/parse-string (:body results) true)]
        (is (some? (:meta (first (:items body)))) "did not find a meta tag")
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics will not work with bad parameters"
      (let [results (search-request "grids.json" "fake=parameter")
            status (:status results)
            body (:body results)]
        (is (= 400 status) "wrong http status")
        (is (string/includes? body "Parameter [fake] was not recognized.") 
            "Parameter validation is wrong.")))

    (testing "Test that generics will work with concept searches."
      (let [results (search-request (format "concepts/%s" concept-id) "")
            status (:status results)]
        (is (= 200 status) "wrong http status")))

    (testing "Test that generics will work with concept and revision searches."
      (let [results (search-request (format "concepts/%s/%s" concept-id revision-id) "")
            status (:status results)]
        (is (= 200 status) "wrong http status")))))
