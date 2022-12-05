(ns cmr.system-int-test.search.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.config :as cfg]
   [cmr.common.util :as util :refer [are3]]
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

(defn get-files-per-concept
  "Using the passed in concept type, find all concept-type version
  directories and build a map of concept-type name with a list of example
  metadata.json files. The structure looks like:
  {\"grid\" (\"schemas/grid/v0.0.1/metadata.json\")}"
  [concept-type]
  {(name concept-type)
   (for [version (concept-type (cfg/approved-pipeline-documents))]
     (str "schemas/" (name concept-type) "/v" version "/metadata.json"))})

(defn get-example-dirs
  "Creates a list of maps. Each map consists of a concept-type name as the key
  and a list of metadata example files for each."
  []
  (map get-files-per-concept (keys (cfg/approved-pipeline-documents))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Test that generic concepts can be searched and have the search results use XML.
(deftest all-generic-search-results-test
  (doseq [example-dirs (get-example-dirs)]
    (let [concept-type-string (key (first (seq example-dirs)))
          dir-list (val (first (seq example-dirs)))]
      (doseq [dir dir-list]
        (let [file (json/parse-string (slurp (io/resource dir)) true)
              native-id (format "Generic-Test-%s" (UUID/randomUUID))
              name (:Name file)
              plural-concept-type-name (inf/plural concept-type-string)
              generic-requester (partial gen-util/generic-request nil "PROV1" native-id concept-type-string)
              good-generic-requester (partial generic-requester file)
              post-results (good-generic-requester :post)
              body (json/parse-string (:body post-results) true)
              concept-id (:concept-id body)
              revision-id (:revision-id body)
              guid-id (:Id file)
              provider "PROV1"]
          (index/wait-until-indexed)
          ;; TODO Need to add in the options parameter
          (testing "Testing the name search"
            (are3 [plural-concept-type-name search-parameter name-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" name-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                ;; (println "The search paramter" (str search-parameter "=" name))
                ;; (println results)
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "Name exact match"
              (inf/plural concept-type-string)
              "name"
              name
              nil

              "Upper case name"
              (inf/plural concept-type-string)
              "name"
              (string/upper-case name)
              nil

              "Upper case name ignore case true (default)"
              (inf/plural concept-type-string)
              "name"
              (string/upper-case name)
              "options[name][ignore-case]=true"

              "name, ignore case false, requires exact match"
              (inf/plural concept-type-string)
              "name"
              name
              "options[name][ignore-case]=false"

              "lower case name passing pattern option as false"
              (inf/plural concept-type-string)
              "name"
              (string/lower-case name)
              "options[name][pattern]=false"
              ;; replace the last character with a * to test wildcard search
              "Test using name with wildcard e.g name[removed last char]*"
              (inf/plural concept-type-string)
              "name"
              (str (string/join "" (drop-last name)) "*")
              "options[name][pattern]=true"))
              ;; Do we need to do 'id'?
          (testing "Testing provider and provider variations"
            (are3 [plural-concept-type-name search-parameter provider-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" provider-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                ; (println "The body of the request" body)
                ;; (println "The search paramter" (str search-parameter "=" name))
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "Test are3 using provider"
              (inf/plural concept-type-string)
              "provider"
              "PROV1"
              nil

              "Test are3 using provider_id"
              (inf/plural concept-type-string)
              "provider-id"
              "PROV1"
              nil

              "Test are3 using provider-id, pass ignore case option false"
              (inf/plural concept-type-string)
              "provider_id"
              "PROV1"
              "options[name][ignore-case]=false"

              "Test using lowercase providerm pass ignore case option true"
              (inf/plural concept-type-string)
              "providerId"
              "prov1"
              "options[name][ignore-case]=true"

              "Test using mixed case prov1, passing default pattern value"
              (inf/plural concept-type-string)
              "providerId"
              "PrOv1"
              "options[provider][pattern]=false"

              "testing provider pattern match"
              (inf/plural concept-type-string)
              "provider"
              "PRO*"
              "options[provider][pattern]=true"))

          (testing "Testing concept id and concept-id variations"
            (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                ;; (println "The body of the request" body)
                ;; (println "The search paramter" (str search-parameter "=" name))
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "search using concept-id"
              (inf/plural concept-type-string)
              "concept-id"
              concept-id
              nil

              "search using concept_id"
              (inf/plural concept-type-string)
              "concept_id"
              concept-id
              nil

              "search using conceptId"
              (inf/plural concept-type-string)
              "conceptId"
              concept-id
              nil))
;; TODO add cases for option flags native id, add the guid 'id' parameter's tests
        (testing "Testing parameter of native_id"
          (are3 [plural-concept-type-name search-parameter native-id-parameter options-flag]
            (let [results (search-request plural-concept-type-name (str search-parameter "=" native-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                  status (:status results)
                  body (:body results)]
              ;; (println "The body of the request" body)
              ;; (println "The search paramter" (str search-parameter "=" name))
              (is (string/includes? body name) "record not found")
              (is (= 200 status) "wrong http status"))

            "search using native_id"
            (inf/plural concept-type-string)
            "native_id"
            native-id
            nil

            "search using native-id"
            (inf/plural concept-type-string)
            "native-id"
            native-id
            nil

            "search using nativeId, exact case search"
            (inf/plural concept-type-string)
            "nativeId"
            native-id
            "options[native-id][ignore-case]=false"

            "search using nativeId, and ignoring case"
            (inf/plural concept-type-string)
            "nativeId"
            (string/upper-case native-id)
            "options[native-id][ignore-case]=true"

            "search using native-id pattern paramter search"
            (inf/plural concept-type-string)
            "native-id"
            (str (string/join "" (drop-last native-id)) "*")
            "options[native-id][pattern]=true"))
            ;; test for legacy documents but, some generics do not have a guid-id
            (if guid-id
              (testing "Testing id (GUID) parameter search"
              (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                      (println "The guid id for the current record" guid-id " of type " plural-concept-type-name)
                  ;; (println "The body of the request" body)
                  ;; (println "The search paramter" (str search-parameter "=" name))
                  (is (string/includes? body name) "record not found")
                  (is (= 200 status) "wrong http status"))

                "search using id(guid)"
                (inf/plural concept-type-string)
                "id"
                guid-id
                nil)))

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
              (is (= 200 status) "wrong http status")))

          (testing "Search generic concept by native-id"
            (let [results (search-request plural-concept-type-name (str "native-id=" native-id))
                  status (:status results)
                  body (:body results)]
              (is (string/includes? body concept-id) "record not found")
              (is (= 200 status) "wrong http status")))

          (testing "Search generic concept by concept-id"
            (let [results (search-request plural-concept-type-name (str "concept-id=" concept-id))
                  status (:status results)
                  body (:body results)]
              (is (string/includes? body concept-id) "record not found")
              (is (= 200 status) "wrong http status")))
          (testing "Search generic concept by provider"
            (let [results (search-request plural-concept-type-name (str "provider=" provider))
                  status (:status results)
                  body (:body results)]
              (is (string/includes? body concept-id) "record not found")
              (is (= 200 status) "wrong http status"))))))))
