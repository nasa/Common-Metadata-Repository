(ns cmr.indexer.test.services.index-versions
  "Integration test for index versions during save and delete in elasticsearch"
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.indexer.data.index-set :as idx-set]
            [cmr.common.cache :as cache]
            [clojure.data.codec.base64 :as b64]
            [cmr.common.lifecycle :as lifecycle]))

(defn- es-doc
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "C1234-PROV1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        version-id "1"
        project-short-names ["ESI" "EPI" "EVI"]]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title.lowercase (s/lower-case entry-title)
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :short-name short-name
     :short-name.lowercase (s/lower-case short-name)
     :version-id version-id
     :version-id.lowercase (s/lower-case version-id)
     :project-sn project-short-names
     :project-sn.lowercase (map s/lower-case project-short-names)}))


(def test-config
  "Return the configuration for elasticsearch"
  {:host "localhost"
   :port 9213
   :admin-token (str "Basic " (b64/encode (.getBytes "password")))})

(def context (atom nil))

(defn server-setup
  "Fixture that starts an instance of elastic in the JVM runs the tests and then shuts it down."
  [f]

  (let [http-port (:port test-config)
        server (lifecycle/start (elastic-server/create-server http-port 9215 "es_data/indexer_test") nil)]
    (reset! context {:system {:db {:conn (esr/connect (str "http://localhost:" http-port))}}})

    ;; Disables standard out logging during testing because it breaks the JUnit parser in bamboo.
    (timbre/set-config! [:appenders :standard-out :enabled?] false)

    (try
      (f)
      (finally
        (lifecycle/stop server nil)))))

;; Run once for the whole test suite
(use-fixtures :once server-setup)

(defn- assert-version
  "Assert the retrieved document for the given id is of the given version"
  [id version]
  (let [result (es/get-document @context "tests" "collection" id)]
    (is (= version (:_version result)))))

(defn- assert-delete
  "Assert the document with the given id is deleted"
  [id]
  (let [result (es/get-document @context "tests" "collection" id)]
    (is (nil? result))))

(defn index-setup
  "Fixture that creates an index and drops it."
  [f]
  (let [conn (get-in @context [:system :db :conn])]
    (esi/create conn "tests" :settings idx-set/collection-setting :mappings idx-set/collection-mapping)
    (try
      (f)
      (finally
        (esi/delete conn "tests")))))
;; Run once for each test to clear out data.
(use-fixtures :each index-setup)


(deftest save-with-increment-versions-test
  (testing "Save with increment versions"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 1 false)
    (assert-version "C1234-PROV1" 1)
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 2 false)
    (assert-version "C1234-PROV1" 2)
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 10 false)
    (assert-version "C1234-PROV1" 10)))

;; TODO update this test with ignore-conflict true/false after the external_gte support is added
(deftest save-with-equal-versions-test
  (testing "Save with equal versions"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 1 true)
    (assert-version "C1234-PROV1" 1)
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 1 true)
    (assert-version "C1234-PROV1" 1)))

(deftest save-with-earlier-versions-test
  (testing "Save with earlier versions with ignore-conflict false"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 3 false)
    (assert-version "C1234-PROV1" 3)
    (try
      (es/save-document-in-elastic @context "tests" "collection" (es-doc) 2 false)
      (catch clojure.lang.ExceptionInfo e
        (let [type (:type (ex-data e))
              err-msg (first (:errors (ex-data e)))]
          (is (= :conflict type))
          (is (re-find #"version conflict, current \[3\], provided \[2\]" err-msg))))))
  (testing "Save with earlier versions with ignore-conflict true"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 3 true)
    (assert-version "C1234-PROV1" 3)
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 2 true)
    (assert-version "C1234-PROV1" 3)))

(deftest delete-with-increment-versions-test
  (testing "Delete with increment versions"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 1 false)
    (es/delete-document @context test-config "tests" "collection" "C1234-PROV1" "2" false)
    (assert-delete "C1234-PROV1")
    (es/delete-document @context test-config "tests" "collection" "C1234-PROV1" "8" false)
    (assert-delete "C1234-PROV1")))

(deftest delete-with-equal-versions-test
    (testing "Delete with equal versions"
      (es/save-document-in-elastic @context "tests" "collection" (es-doc) 1 false)
      (es/delete-document @context test-config "tests" "collection" "C1234-PROV1" "1" false)
      (assert-delete "C1234-PROV1")))

(deftest delete-with-earlier-versions-test
  (testing "Delete with earlier versions ignore-conflict false"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 2 false)
    (try
      (es/delete-document @context test-config "tests" "collection" "C1234-PROV1" "1" false)
      (catch java.lang.Exception e
        (is (re-find #"version conflict, current \[2\], provided \[1\]" (.getMessage e))))))
  (testing "Delete with earlier versions ignore-conflict true"
    (es/save-document-in-elastic @context "tests" "collection" (es-doc) 2 true)
    (es/delete-document @context test-config "tests" "collection" "C1234-PROV1" "1" true)
    (assert-version "C1234-PROV1" 2)))

