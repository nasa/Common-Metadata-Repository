(ns cmr.system-int-test.search.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.concepts :as concepts]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [inflections.core :as inf])
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

(defn find-example-metadata-holding-dirs
  "Goes through all of the directories and only uses the ones that contain v.#.#* in them.
  These are the directories that contain the metadata.json example files. It is done this
  way to support muliple versions with any number of minor versions in them."
  [concept-file-structure]
  (let [concept-type-name (first (keys concept-file-structure))]
    {concept-type-name (->> (get concept-file-structure concept-type-name)
                            (map #(re-find #".*/v\d\.\d.*" (str %)))
                            (remove nil?)
                            (flatten))}))


(defn set-concept-type-to-file-structure
  "This function sets up a map structure for the all-generic-search-results-test.
   The structure looks like:
   {\"grid\" (/Users/eereiter/work/clojure/Common-Metadata-Repository-assoc/schemas/resources/schemas/grid/v0.0.1)}"
  [generic-concept-type-name]
  {generic-concept-type-name (-> (str "schemas/" generic-concept-type-name "/")
                                 (io/resource)
                                 (io/file)
                                 (.listFiles)
                                 (seq))})

(defn get-example-dirs
  "Creates a list of maps. Each map consists of a concept-type name as the key
  and a list of metadata example files for each."
  []
  (let [generic-concept-type-names (->> (concepts/get-generic-concept-types-array)
                                        (map #(name %))
                                        (remove #{"generic" "index"}))
        dir-list (map set-concept-type-to-file-structure generic-concept-type-names)]
    (map #(find-example-metadata-holding-dirs %) dir-list)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Test that generic concepts can be searched and have the search results use XML.
(deftest all-generic-search-results-test
  (doseq [example-dirs (get-example-dirs)]
    (let [concept-type-string (key (first (seq example-dirs)))
          dir-list (val (first (seq example-dirs)))]
      (doseq [dir dir-list]
        (let [file (json/parse-string (slurp (str dir "/metadata.json")) true)
              native-id (format "Generic-Test-%s" (UUID/randomUUID))
              name (:Name file)
              plural-concept-type-name (inf/plural concept-type-string)
              generic-requester (partial gen-util/generic-request nil "PROV1" native-id concept-type-string)
              good-generic-requester (partial generic-requester file)
              post-results (good-generic-requester :post)
              body (json/parse-string (:body post-results) true)
              concept-id (:concept-id body)
              revision-id (:revision-id body)]
          (index/wait-until-indexed)

          (testing "Check that test the document ingested before going forward with tests"
            (is (= 201 (:status post-results))"failed to ingest test record"))

          (testing "Test that generics can use XML search results."
            (let [results (search-request plural-concept-type-name (str "name=" name))
                  status (:status results)
                  body (:body results)]
              (is (string/includes? body name) "record not found")
              (is (= 200 status) "wrong http status")))

          (testing "Test that generics can use JSON search results."
            (let [results (search-request (str plural-concept-type-name ".json") (str "name=" name))
                  status (:status results)
                  body (json/parse-string (:body results) true)]
              (is (some? (:concept_id (first (:items body)))) "no concept id")
              (is (= 200 status) "wrong http status")))

          (testing "Test that generics can use UMM_JSON search results."
            (let [results (search-request (str plural-concept-type-name ".umm_json") (str "name=" name))
                  status (:status results)
                  body (json/parse-string (:body results) true)]
              (is (some? (:meta (first (:items body)))) "did not find a meta element")
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
              (is (= 200 status) "wrong http status"))))))))
