(ns cmr.indexer.test.services.index-fields
  "Integration tests for indexing fields with save and delete operations in
  elasticsearch."
  (:require
   [clojure.data.codec.base64 :as b64]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojurewerkz.elastisch.rest :as esr]
   [clojurewerkz.elastisch.rest.document :as doc]
   [clojurewerkz.elastisch.rest.index :as esi]
   [cmr.common.cache :as cache]
   [cmr.common.lifecycle :as lifecycle]
   [cmr.common.test.test-util :as tu]
   [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
   [cmr.indexer.data.elasticsearch :as es]
   [cmr.indexer.data.index-set :as idx-set]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Constants & Utility Functions   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def test-config
  "Return the configuration for elasticsearch"
  {:host "localhost"
   :port 9213
   :admin-token (str "Basic " (b64/encode (.getBytes "password")))})

(def context (atom nil))

(defn- save-document-in-elastic
  "Helper function to call elasticsearch save-document-in-elastic"
  ([context es-type es-doc concept-id revision-id]
   (save-document-in-elastic
    context es-type es-doc concept-id revision-id {}))
  ([context es-type es-doc concept-id revision-id options]
   (save-document-in-elastic
    context ["tests"] es-type es-doc concept-id revision-id options))
  ([context es-index es-type es-doc concept-id revision-id options]
   (es/save-document-in-elastic
    context es-index es-type es-doc concept-id revision-id revision-id
    options)))

(defn- delete-document-in-elastic
  ([context es-type concept-id revision-id]
   (delete-document-in-elastic
    context es-type concept-id revision-id {}))
  ([context es-type concept-id revision-id options]
    (delete-document-in-elastic
     context ["tests"] es-type concept-id revision-id options))
  ([context es-index es-type concept-id revision-id options]
   (es/delete-document
    context es-index es-type concept-id revision-id revision-id options)))

(defn- assert-version
  "Assert the retrieved document for the given id is of the given version.
  Note that this will only be valid for concept type ES documents that have
  the version field, i.e.: collections and granules."
  [concept-type-str id version]
  (let [result (es/get-document @context "tests" concept-type-str id)]
    (is (= version (:_version result)))))

(defn- assert-delete
  "Assert the document with the given id is deleted"
  [concept-type-str id]
  (let [result (es/get-document @context "tests" concept-type-str id)]
    (is (nil? result))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Testing Fixtures   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn server-setup
  "Fixture that starts an instance of elastic in the JVM runs the tests and then shuts it down."
  [f]
  (let [http-port (:port test-config)
        server (lifecycle/start (elastic-server/create-server http-port 9215 "es_data/indexer_test") nil)]
    (reset! context {:system {:db {:config test-config
                                   :conn (esr/connect (str "http://localhost:" http-port))}}})
    (try
      (f)
      (finally
        (lifecycle/stop server nil)))))

(defn index-setup
  "Fixture that creates an index and drops it."
  [f]
  (let [conn (get-in @context [:system :db :conn])]
    (esi/create conn "tests" :settings idx-set/collection-setting-v2 :mappings idx-set/collection-mapping)
    (try
      (f)
      (finally
        (esi/delete conn "tests")))))

;; Run once for the whole test suite
(use-fixtures
  :once
  (join-fixtures [;; Disables standard out logging during testing because it breaks the JUnit parser in bamboo.
                  tu/silence-logging-fixture
                  server-setup]))

;; Run once for each test to clear out data.
(use-fixtures :each index-setup)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;   Tests   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;; Using this method of pulling in files is inspired by something similar
;;; done in clojure.pprint to organize the API there (for more details on
;;; this, see https://github.com/clojure/clojure/blob/e19157c4809622fcaac1d8ccca8e3f6a67b3d848/src/clj/clojure/pprint.clj).
;;;
;;; Benfits for us in tests include:
;;;  * keeping this test file of a manageable size
;;;  * keeping tests for different concepts organized in different files
;;;  * doing both of these without incurring additional overhead for
;;;    Elasticsearch setup (as there would be if these tests were split
;;;    across different namespaces)
;;;
;;; Keep in mind, however, that the onus is on the developer to ensure that
;;; all function and test names are unique in all these files.
(load "index_fields/collections")
