(ns cmr.system-int-test.search.misc.generics-test
  "Tests for the Generic Concept Search API"
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.config :as cfg]
   [cmr.common.generics :as generics]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util :refer [are3]]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.system :as system]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.system-int-test.utils.generic-util :as gen-util]
   [cmr.umm-spec.umm-spec-core :as spec]
   [inflections.core :as inf])
  (:import
   [java.util UUID]))

(defn grant-all-generic-permission-fixture
  "A test fixture that grants all users the ability to create and modify generic documents."
  [f]
  (echo-util/grant-system-ingest-management (system/context) [:read :update] [:read :update])
  (f))

(use-fixtures :each (join-fixtures [(ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"})
                                    grant-all-generic-permission-fixture
                                    ;;allow draft ingest permission for guest on PROV1, but not on PROV2.
                                    (gen-util/grant-all-drafts-fixture {"provguid1" "PROV1"}
                                                                       [:read]
                                                                       [:read])]))
(defn search-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([concept-type-ext params]
   (-> {:method :get
        :url (format "%s%s?%s" (url-helper/search-root) concept-type-ext params)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false}
       (client/request))))


(defn search-request-version-url-extension
  "This function will make a request to one of the generic URLs using and extended url to include version"
  [concept-type-ext url-extension]
   (-> {:method :get
        :url (format "%s%s.%s" (url-helper/search-root) concept-type-ext url-extension)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false}
       (client/request)))


(defn search-request-version-accept-header
  "This function will make a request to one of the generic URLs passing the accept header to specify version"
  [concept-type-ext accept]
   (-> {:method :get
        :url (format "%s%s" (url-helper/search-root) concept-type-ext)
        :connection-manager (system/conn-mgr)
        :throw-exceptions false
        :accept accept}
       (client/request)))

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
              name (or (:Name file)
                       (:ShortName file))
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
          (testing "Testing generics name parameter search"
            (are3 [plural-concept-type-name search-parameter name-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" name-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "Name exact match"
              (inf/plural concept-type-string)
              "name"
              name
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
              ;; Replace the last character with a * to test wildcard search
              "Test using name with pattern parameter e.g name[removed last char]*"
              (inf/plural concept-type-string)
              "name"
              (str (string/join "" (drop-last name)) "*")
              "options[name][pattern]=true"))

          (testing "Testing generics provider parameter search"
            (are3 [plural-concept-type-name search-parameter provider-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" provider-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "Test provider parameter search"
              (inf/plural concept-type-string)
              "provider"
              "PROV1"
              nil

              "Test provider_id parameter search, passing default pattern option"
              (inf/plural concept-type-string)
              "provider-id"
              "PROV1"
              "options[provider][pattern]=false"

              "Test provider-id parameter search, pass ignore case option false"
              (inf/plural concept-type-string)
              "provider_id"
              "PROV1"
              "options[name][ignore-case]=false"

              "Test using lower-case provider pass ignore case option true"
              (inf/plural concept-type-string)
              "providerId"
              "prov1"
              "options[name][ignore-case]=true"

              "testing provider pattern match"
              (inf/plural concept-type-string)
              "provider"
              "PRO*"
              "options[provider][pattern]=true"))

          (testing "Testing generics concept-id parameter search"
            (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
              (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                    status (:status results)
                    body (:body results)]
                (is (string/includes? body name) "record not found")
                (is (= 200 status) "wrong http status"))

              "Test concept-id parameter search"
              (inf/plural concept-type-string)
              "concept-id"
              concept-id
              nil

              "Test concept_id parameter search"
              (inf/plural concept-type-string)
              "concept_id"
              concept-id
              nil

              "Test conceptId parameter search"
              (inf/plural concept-type-string)
              "conceptId"
              concept-id
              nil))

        (testing "Testing parameter of native_id"
          (are3 [plural-concept-type-name search-parameter native-id-parameter options-flag]
            (let [results (search-request plural-concept-type-name (str search-parameter "=" native-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                  status (:status results)
                  body (:body results)]
              (is (string/includes? body name) "record not found")
              (is (= 200 status) "wrong http status"))

            "Test native_id parameter search"
            (inf/plural concept-type-string)
            "native_id"
            native-id
            nil

            "Test native-id parameter search"
            (inf/plural concept-type-string)
            "native-id"
            native-id
            nil

            "Search using nativeId, exact case search"
            (inf/plural concept-type-string)
            "nativeId"
            native-id
            "options[native-id][ignore-case]=false"

            "Search using nativeId, and ignoring case true (default)"
            (inf/plural concept-type-string)
            "nativeId"
            (string/upper-case native-id)
            "options[native-id][ignore-case]=true"

            "Search using native-id pattern parameter search"
            (inf/plural concept-type-string)
            "native-id"
            (str (string/join "" (drop-last native-id)) "*")
            "options[native-id][pattern]=true"))
            ;; Test for legacy documents but, some generics do not have a guid-id
            ;; Searching for a generic without an Id, namely grids will result in matching all grids
            ;; Id is not a valid search parameter for draft records.
            (if (and guid-id
                     (not (string/includes? concept-type-string "draft")))
              (testing "Testing id (GUID), the identifier that was assigned from legacy system in the parameter search"
              (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                  (is (string/includes? body name) "record not found")
                  (is (= 200 status) "wrong http status"))

                "Search using id(guid)"
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
              (is (= 200 status) "wrong http status")))

           (testing "Searching with non-existent UMM JSON version in the url suffix for generics"
             (are3 [url-extension]
                   (let [{:keys [status body]} (search-request-version-url-extension plural-concept-type-name url-extension)]
                     (is (= 400 status))
                     (is (= (format "{\"errors\":[\"The mime type [application/vnd.nasa.cmr.umm_results+json] with version [9.9.9] is not supported for %s.\"]}" plural-concept-type-name)
                            body)))

                   "explicit UMM JSON version through suffix"
                   "umm_json_v9_9_9"))

             (testing "Searching with existing UMM JSON version in the url suffix for generics"
               (are3 [url-extension]
                     (let [{:keys [status body]} (search-request-version-url-extension plural-concept-type-name url-extension)]
                       (is (= 200 status))
                       (is (is (string/includes? body concept-id) "record not found")))

               "explicit UMM JSON version through suffix"
               (format "umm_json_v%s" (string/replace (generics/current-generic-version (keyword concept-type-string)) #"\." "_"))))

           (testing "Searching with non-existent UMM JSON version for generics passing the accept header"
             (are3 [acceptHeader]
                   (let [{:keys [status body]} (search-request-version-accept-header plural-concept-type-name acceptHeader)]
                     (is (= 400 status))
                     (is (= (format "{\"errors\":[\"The mime type [application/vnd.nasa.cmr.umm_results+json] with version [9.9.9] is not supported for %s.\"]}" plural-concept-type-name)
                            body)))

                   "explicit UMM JSON version through suffix"
                   "application/vnd.nasa.cmr.umm+json;version=9.9.9"))

           (testing "Searching with existing UMM JSON version for generics passing the accept header"
             (are3 [acceptHeader]
                   (let [results (search-request-version-accept-header plural-concept-type-name acceptHeader) status (:status results)
                         body (:body results)]
                     (is (= 200 status))
                     (is (is (string/includes? body concept-id) "record not found")))

                   "explicit UMM JSON version through suffix"
                   (format "application/vnd.nasa.cmr.umm+json;version=%s" (generics/current-generic-version (keyword concept-type-string))))))))))

;; Test that generic draft collections non json records can be ingested and searched.
(deftest all-generic-non-json-collection-draft-search-results-test
  (let [dif10-file {mt/dif10 (slurp (io/resource "CMR-5138-DIF10-DataDates-Provided.xml"))}
        echo10-file {mt/echo10 (slurp (io/resource "echo10-samples/collection.echo10"))}
        iso-file {mt/iso19115 (slurp (io/resource "iso-samples/5216_IsoMends_Collection.xml"))}
        files (concat dif10-file echo10-file iso-file)]
    (doseq [file files]
      (let [mime-type (first file)
            metadata (second file)
            sanitized-collection (cmr.umm-spec.umm-spec-core/parse-metadata {:ignore-kms-keywords true} :collection mime-type metadata)
            name (:ShortName sanitized-collection)
            native-id (format "Generic-Test-%s" (UUID/randomUUID))
            concept-type-string "collection-draft"
            post-results (gen-util/generic-request nil "PROV1" native-id concept-type-string metadata :put mime-type)
            body (json/parse-string (:body post-results) true)
            concept-id (:concept-id body)
            revision-id (:revision-id body)
            plural-concept-type-name (inf/plural concept-type-string)]
        (index/wait-until-indexed)

        (testing "Check that test the document ingested before going forward with tests"
          (is (= 201 (:status post-results)) "failed to ingest test record"))

        (testing "Testing draft collection generic name parameter search"
          (are3 [plural-concept-type-name search-parameter name-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" name-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                  (is (string/includes? body name) "record not found")
                  (is (= 200 status) "wrong http status"))

                "Name exact match"
                (inf/plural concept-type-string)
                "name"
                name
                nil

                "Upper case name ignore case true (default)"
                (inf/plural concept-type-string)
                "name"
                (string/upper-case name)
                "options[name][ignore-case]=true"))

        (testing "Testing generics concept-id parameter search"
          (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                  (is (string/includes? body name) "record not found")
                  (is (= 200 status) "wrong http status"))

                "Test concept-id parameter search"
                (inf/plural concept-type-string)
                "concept-id"
                concept-id
                nil))

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

        (testing "Test that generics will work with concept searches."
          (let [results (search-request (format "concepts/%s" concept-id) "")
                status (:status results)]
            (is (= 200 status) "wrong http status")))

        (testing "Test that generics will work with concept and revision searches."
          (let [results (search-request (format "concepts/%s/%s" concept-id revision-id) "")
                status (:status results)]
            (is (= 200 status) "wrong http status")))))))

;; Test that generic draft collections can not be searched on PROV2 that doesn't grant guest PROVIDER_CONTEXT read permission .
(deftest all-generic-non-acl-collection-draft-search-results-test
  (let [dif10-file {mt/dif10 (slurp (io/resource "CMR-5138-DIF10-DataDates-Provided.xml"))}
        echo10-file {mt/echo10 (slurp (io/resource "echo10-samples/collection.echo10"))}
        iso-file {mt/iso19115 (slurp (io/resource "iso-samples/5216_IsoMends_Collection.xml"))}
        files (concat dif10-file echo10-file iso-file)]
    (doseq [file files]
      (let [mime-type (first file)
            metadata (second file)
            sanitized-collection (cmr.umm-spec.umm-spec-core/parse-metadata {:ignore-kms-keywords true} :collection mime-type metadata)
            name (:ShortName sanitized-collection)
            native-id (format "Generic-Test-%s" (UUID/randomUUID))
            concept-type-string "collection-draft"
            ;; need to use the system token in order to ingest on PROV2 that doesn't have PROVIDER_CONTEXT permission for the guest.
            post-results (gen-util/generic-request "mock-echo-system-token" "PROV2" native-id concept-type-string metadata :put mime-type)
            body (json/parse-string (:body post-results) true)
            concept-id (:concept-id body)
            revision-id (:revision-id body)
            plural-concept-type-name (inf/plural concept-type-string)]
        (index/wait-until-indexed)

        (testing "Check that test the document ingested before going forward with tests"
          (is (= 201 (:status post-results)) "failed to ingest test record"))

        (testing "Testing draft collection generic name parameter search"
          (are3 [plural-concept-type-name search-parameter name-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" name-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                  ;; records shouldn't be found because no acl permission.
                  (is (not (string/includes? body name)) "record found")
                  (is (= 200 status) "wrong http status"))

                "Name exact match"
                (inf/plural concept-type-string)
                "name"
                name
                nil

                "Upper case name ignore case true (default)"
                (inf/plural concept-type-string)
                "name"
                (string/upper-case name)
                "options[name][ignore-case]=true"))

        (testing "Testing generics concept-id parameter search"
          (are3 [plural-concept-type-name search-parameter concept-id-parameter options-flag]
                (let [results (search-request plural-concept-type-name (str search-parameter "=" concept-id-parameter (if options-flag (str "&" options-flag) (str ""))))
                      status (:status results)
                      body (:body results)]
                  (is (not (string/includes? body name)) "record found")
                  (is (= 200 status) "wrong http status"))

                "Test concept-id parameter search"
                (inf/plural concept-type-string)
                "concept-id"
                concept-id
                nil))

        (testing "Test that generics can use XML search results."
          (let [results (search-request plural-concept-type-name (str "name=" name))
                status (:status results)
                body (:body results)]
            (is (not (string/includes? body name)) "record found")
            (is (= 200 status) "wrong http status")))

        (testing "Test that generics can use JSON search results."
          (let [results (search-request (str plural-concept-type-name ".json") (str "name=" name))
                status (:status results)
                body (json/parse-string (:body results) true)]
            (is (not (some? (:concept_id (first (:items body))))) "found concept id")
            (is (= 200 status) "wrong http status")))

        (testing "Test that generics can use UMM_JSON search results."
          (let [results (search-request (str plural-concept-type-name ".umm_json") (str "name=" name))
                status (:status results)
                body (json/parse-string (:body results) true)]
            (is (not (some? (:meta (first (:items body))))) "found a meta element")
            (is (= 200 status) "wrong http status")))

        (testing "Test that generics will work with concept searches."
          (let [results (search-request (format "concepts/%s" concept-id) "")
                status (:status results)]
            (is (= 404 status) "wrong http status")))

        (testing "Test that generics will work with concept and revision searches."
          (let [results (search-request (format "concepts/%s/%s" concept-id revision-id) "")
                status (:status results)]
            (is (= 404 status) "wrong http status")))))))
