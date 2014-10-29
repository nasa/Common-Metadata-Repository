(ns cmr.system-int-test.bootstrap.db-consistency-test
  "This tests putting the Catalog REST and Metadata DB in an inconsistent state and then using
  the bootstrap application to make them consistent again."
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.utils.bootstrap-util :as bootstrap]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.umm.echo10.core :as echo10]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.utils.test-environment :as test-env]
            [cmr.bootstrap.test.catalog-rest :as cat-rest]
            ))


(use-fixtures :each (bootstrap/db-fixture "CPROV1"))

;; TODO add a fixture that will reset metadata db and one that will delete the provider tables
;; we create from catalog rest and then recreate those provider tables

;; TODO rename this test

;; TODO wrap in env test to see if using db

(comment
  (bootstrap/db-fixture-setup "CPROV1")

  (def concept (let [entry-title "coll1"
                     coll1 (dc/collection {:entry-title entry-title})
                     xml (echo10/umm->echo10-xml coll1)]
                 {:concept-type :collection
                  :format "application/echo10+xml"
                  :metadata xml
                  :concept-id "C1-CPROV1"
                  :revision-id 1
                  :deleted false
                  :extra-fields {:short-name (get-in coll1 [:product :short-name])
                                 :entry-title entry-title
                                 :version-id (get-in coll1 [:product :version-id])
                                 :delete-time nil}
                  :provider-id "CPROV1"
                  :native-id entry-title}))

  (cat-rest/save-concept (bootstrap/system) concept)

  (bootstrap/bulk-migrate-provider "CPROV1")

  (=
    (dissoc (ingest/get-concept "C1-CPROV1") :revision-date)
     concept)

  (bootstrap/db-fixture-tear-down "CPROV1")



  )

(deftest bulk-migrate-collection-test
  (test-env/only-with-real-database
    (let [entry-title "coll1"
          coll1 (dc/collection {:entry-title entry-title})
          xml (echo10/umm->echo10-xml coll1)
          concept {:concept-type :collection
                   :format "application/echo10+xml"
                   :metadata xml
                   :concept-id "C1-CPROV1"
                   :revision-id 1
                   :deleted false
                   :extra-fields {:short-name (get-in coll1 [:product :short-name])
                                  :entry-title entry-title
                                  :version-id (get-in coll1 [:product :version-id])
                                  :delete-time nil}
                   :provider-id "CPROV1"
                   :native-id entry-title}
          system (bootstrap/system)]

      (cat-rest/save-concept system concept)

      (is (= {:status 202
              :message "Processing provider CPROV1"}
             (bootstrap/bulk-migrate-provider "CPROV1")))

      (is (= concept
            (dissoc (ingest/get-concept "C1-CPROV1") :revision-date)))


      )))
