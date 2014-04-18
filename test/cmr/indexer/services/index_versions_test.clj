(ns ^{:doc "Integration test for index versions during save and delete in elasticsearch"}
  cmr.indexer.services.index-versions-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [taoensso.timbre :as timbre]
            [clojurewerkz.elastisch.rest :as esr]
            [clojurewerkz.elastisch.rest.index :as esi]
            [clojurewerkz.elastisch.rest.document :as doc]
            [cmr.indexer.config.elasticsearch-config :as conf]
            [cmr.elastic-utils.embedded-elastic-server :as elastic-server]
            [cmr.indexer.data.elasticsearch :as es]
            [cmr.common.lifecycle :as lifecycle]))


(def collection-setting { "index"
                         {"number_of_shards" 2
                          "number_of_replicas"  1
                          "refresh_interval" "1s"}})
(def collection-mapping
  {"collection" { "dynamic"  "strict"
                 "_source"  {"enabled" false}
                 "_all"     {"enabled" false}
                 "_id"      {"path" "concept-id"}
                 :properties {:concept-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                              :entry-title {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                              :entry-title.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                              :provider-id {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                              :provider-id.lowercase {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                              :short-name  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                              :short-name.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                              :version-id  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs" :store "yes"}
                              :version-id.lowercase  {:type "string" :index "not_analyzed" :omit_norms "true" :index_options "docs"}
                              :start-date  {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"}
                              :end-date    {:type "date" :format "yyyy-MM-dd'T'HH:mm:ssZ||yyyy-MM-dd'T'HH:mm:ss.SSSZ"}}}})

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

(def test-config
  "Configuration to use for elasticsearch during test run."
  (assoc (conf/config) :port 9205))


(defn server-setup
  "Fixture that starts an instance of elastic in the JVM runs the tests and then shuts it down."
  [f]

  ;; When this test runs as part of dev-systems all tests it changes the global endpoint and breaks
  ;; other tests that run after it. We keep track of the internal endpoint of the elastisch endpoint
  ;; and set it back after the tests have completed.
  (let [current-endpoint esr/*endpoint*
        http-port (:port test-config)
        server (lifecycle/start (elastic-server/create-server http-port 9215) nil)]
    (esr/connect! (str "http://localhost:" http-port))

    ;; Disables standard out logging during testing because it breaks the JUnit parser in bamboo.
    (timbre/set-config! [:appenders :standard-out :enabled?] false)

    (try
      (f)
      (finally
        (lifecycle/stop server nil)
        (alter-var-root (var esr/*endpoint*) (constantly current-endpoint))))))
;; Run once for the whole test suite
(use-fixtures :once server-setup)


(defn index-setup
  "Fixture that creates an index and drops it."
  [f]
  (esi/create "tests" :settings collection-setting :mappings collection-mapping)
  (try
    (f)
    (finally
      (esi/delete "tests"))))
;; Run once for each test to clear out data.
(use-fixtures :each index-setup)


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
    (es/delete-document-in-elastic {} test-config "tests" "collection" "C1234-PROV1" "1" false)
    (assert-delete "C1234-PROV1")
    (es/delete-document-in-elastic {} test-config "tests" "collection" "C1234-PROV1" "8" false)
    (assert-delete "C1234-PROV1")))

; TODO this test needs the new elasticsearch external_gte feature to pass
; https://github.com/elasticsearch/elasticsearch/pull/4993
; Uncomment this test when the feature is released
#_(deftest delete-with-equal-versions-test
    (testing "Delete with equal versions"
      (es/save-document-in-elastic {} "tests" "collection" (es-doc) 0 false)
      (es/delete-document-in-elastic {} test-config "tests" "collection" "C1234-PROV1" "0" false)
      (assert-delete "C1234-PROV1")))

(deftest delete-with-earlier-versions-test
  (testing "Delete with earlier versions ignore-conflict false"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 false)
    (try
      (es/delete-document-in-elastic {} test-config "tests" "collection" "C1234-PROV1" "1" false)
      (catch java.lang.Exception e
        (is (re-find #"version conflict, current \[2\], provided \[1\]" (.getMessage e))))))
  (testing "Delete with earlier versions ignore-conflict true"
    (es/save-document-in-elastic {} "tests" "collection" (es-doc) 2 true)
    (es/delete-document-in-elastic {} test-config "tests" "collection" "C1234-PROV1" "1" true)
    (assert-version "C1234-PROV1" 2)))

