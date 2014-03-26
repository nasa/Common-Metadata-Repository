(ns ^{:doc "Integration test for index versions during save and delete in elasticsearch"}
  cmr.indexer.services.index-versions-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [cmr.indexer.config.elasticsearch-config :as conf]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.indexer.data.elasticsearch-properties :as es-prop]))

(defn- es-doc
  "Returns dummy elasticsearch doc for testing"
  []
  (let [concept-id "C1234-PROV1"
        entry-title "Dummy entry title"
        provider-id "PROV1"
        short-name "DummyShort"
        version-id "1"]
    {:concept-id concept-id
     :entry-title entry-title
     :entry-title.lowercase (s/lower-case entry-title)
     :provider-id provider-id
     :provider-id.lowercase (s/lower-case provider-id)
     :short-name short-name
     :short-name.lowercase (s/lower-case short-name)
     :version-id version-id
     :version-id.lowercase (s/lower-case version-id)}))

(defn- assert-version
  "Assert the retrieved document for the given id is of the given version"
  [id version]
  (let [result (es/get-document {} "tests" "collection" id)]
    (is (= version (:_version result)))))

(defn- assert-delete
  "Assert the document with the given id is deleted"
  [id]
  (let [result (es/get-document {} "tests" "collection" id)]
    (is (nil? result))))

(defn setup
  "set up the fixtures for test"
  []
  (let [{:keys [host port]} (conf/config)]
    (esr/connect! (str "http://" host ":" port)))
  (esi/create "tests" :settings es-prop/collection-setting :mappings es-prop/collection-mapping))

(defn teardown
  "tear down after the test"
  []
  (esi/delete "tests"))

(defn wrap-setup
  [f]
  (setup)
  (try
    (f)
    (finally (teardown))))

(use-fixtures :each wrap-setup)

(deftest save-with-increment-versions-test
  (testing "Save with increment versions"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 false)
    (assert-version "C1234-PROV1" 0)
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 1 false)
    (assert-version "C1234-PROV1" 1)
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 10 false)
    (assert-version "C1234-PROV1" 10)))

;; TODO update this test with ignore-conflict true/false after the external_gte support is added
(deftest save-with-equal-versions-test
    (testing "Save with equal versions"
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 true)
      (assert-version "C1234-PROV1" 0)
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 true)
      (assert-version "C1234-PROV1" 0)))

(deftest save-with-earlier-versions-test
  (testing "Save with earlier versions with ignore-conflict false"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 3 false)
    (assert-version "C1234-PROV1" 3)
    (try
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 false)
      (catch clojure.lang.ExceptionInfo e
        (let [type (:type (ex-data e))
              err-msg (first (:errors (ex-data e)))]
          (is (= :conflict type))
          (is (re-find #"version conflict, current \[3\], provided \[2\]" err-msg))))))
  (testing "Save with earlier versions with ignore-conflict true"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 3 true)
    (assert-version "C1234-PROV1" 3)
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 true)
    (assert-version "C1234-PROV1" 3)))

(deftest delete-with-increment-versions-test
  (testing "Delete with increment versions"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 false)
    (es/delete-document-in-elastic {} (conf/config) "tests" "collection" "C1234-PROV1" "1" false)
    (assert-delete "C1234-PROV1")
    (es/delete-document-in-elastic {} (conf/config) "tests" "collection" "C1234-PROV1" "8" false)
    (assert-delete "C1234-PROV1")))

; TODO this test needs the new elasticsearch external_gte feature to pass
; https://github.com/elasticsearch/elasticsearch/pull/4993
; Uncomment this test when the feature is released
#_(deftest delete-with-equal-versions-test
    (testing "Delete with equal versions"
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 false)
      (es/delete-document-in-elastic {} (conf/config) "tests" "collection" "C1234-PROV1" "0" false)
      (assert-delete "C1234-PROV1")))

(deftest delete-with-earlier-versions-test
  (testing "Delete with earlier versions ignore-conflict false"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 false)
    (try
      (es/delete-document-in-elastic {} (conf/config) "tests" "collection" "C1234-PROV1" "1" false)
      (catch java.lang.Exception e
        (is (re-find #"version conflict, current \[2\], provided \[1\]" (.getMessage e))))))
  (testing "Delete with earlier versions ignore-conflict true"
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 true)
      (es/delete-document-in-elastic {} (conf/config) "tests" "collection" "C1234-PROV1" "1" true)
      (assert-version "C1234-PROV1" 2)))

