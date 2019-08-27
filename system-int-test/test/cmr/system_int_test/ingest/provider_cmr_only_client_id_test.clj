(ns cmr.system-int-test.ingest.provider-cmr-only-client-id-test
  "CMR provider CMR-ONLY flag and client id integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.data2.core :as d]
    [cmr.system-int-test.data2.granule :as dg]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]
    [cmr.system-int-test.utils.index-util :as index]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.utils.search-util :as search]
    [cmr.umm.umm-granule :as umm-g]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"
                                           "provguid2" "GES_DISC"}))

(def ingest-functions-to-test
  [#'ingest/validate-concept #'ingest/ingest-concept #'ingest/delete-concept])

(defn- assert-ingest-success
  "Executes the given function with the concept and client-id and make sure the status code
  is correct. It will print out the executed function name when test fails."
  [func-var concept client-id]
  (let [{:keys [status errors]} ((var-get func-var) concept {:client-id client-id})]
    (is (#{200 201} status) (format "Failed in %s. with errors: %s" func-var errors))))

(deftest collection-cmr-only-client-id-test
  (testing "ingest operations on CMR-ONLY provider can be submitted by client Echo"
    (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "ECHO"))))
  (testing "ingest operations on CMR-ONLY provider can be submitted by client other than Echo"
    (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "any"))))
  (testing "ingest operations on non CMR-ONLY provider can be submitted by client Echo"
    (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (ingest/update-ingest-provider {:provider-id "PROV1"
                                      :short-name "PROV1"
                                      :cmr-only false
                                      :small false})
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "ECHO"))))
  (testing "ingest operations on non CMR-ONLY provider can be submitted by client other than Echo"
    (let [coll1 (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (ingest/update-ingest-provider {:provider-id "PROV1"
                                      :short-name "PROV1"
                                      :cmr-only false
                                      :small false})
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "any")))))

(deftest granule-cmr-only-client-id-test
  (testing "ingest operations on CMR-ONLY provider can be submitted by client Echo"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (d/item->concept (dg/granule-with-umm-spec-collection collection "C1-PROV1"))]
      (ingest/ingest-concept concept)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "ECHO"))))
  (testing "ingest operations on CMR-ONLY provider can be submitted by client other than Echo"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (d/item->concept (dg/granule-with-umm-spec-collection collection "C1-PROV1"))]
      (ingest/ingest-concept concept)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "any"))))
  (testing "ingest operations on non CMR-ONLY provider can be submitted by client Echo"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (d/item->concept (dg/granule-with-umm-spec-collection collection "C1-PROV1"))]
      (ingest/ingest-concept concept)
      (ingest/update-ingest-provider {:provider-id "PROV1"
                                      :short-name "PROV1"
                                      :cmr-only false
                                      :small false})
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "ECHO"))))
  (testing "ingest operations on non CMR-ONLY provider can be submitted by client other than Echo"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          concept (d/item->concept (dg/granule-with-umm-spec-collection collection "C1-PROV1"))]
      (ingest/ingest-concept concept)
      (ingest/update-ingest-provider {:provider-id "PROV1"
                                      :short-name "PROV1"
                                      :cmr-only false
                                      :small false})
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-success func concept "any")))))

(deftest granule-virtual-product-service-ingest-test
  (testing "ingest with Virtual-Product-Service as client-id should succeed for cmr-only provider"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          granule (dg/granule-with-umm-spec-collection collection "C1-PROV1")
          concept (d/item->concept granule)]
      (assert-ingest-success #'ingest/validate-concept concept "Virtual-Product-Service")
      (assert-ingest-success #'ingest/ingest-concept concept "Virtual-Product-Service")
      (index/wait-until-indexed)
      (is (= 1 (:hits (search/find-refs :granule {:granule-ur (:granule-ur granule)
                                                  :page-size 50}))))
      (assert-ingest-success #'ingest/delete-concept concept "Virtual-Product-Service")
      (index/wait-until-indexed)
      (is (= 0 (:hits (search/find-refs :granule {:granule-ur (:granule-ur granule)
                                                  :page-size 50}))))))
  (testing "ingest with Virtual-Product-Service as client-id should succeed for non cmr-only provider"
    (let [collection (d/ingest-umm-spec-collection "PROV1" (data-umm-c/collection {:EntryTitle "ET1"}))
          granule (dg/granule-with-umm-spec-collection collection "C1-PROV1")
          concept (d/item->concept granule)]
      (ingest/update-ingest-provider {:provider-id "PROV1"
                                      :short-name "PROV1"
                                      :cmr-only false
                                      :small false})
      (ingest/clear-caches)
      (assert-ingest-success #'ingest/validate-concept concept "Virtual-Product-Service")
      (assert-ingest-success #'ingest/ingest-concept concept "Virtual-Product-Service")
      (index/wait-until-indexed)
      (is (= 1 (:hits (search/find-refs :granule {:granule-ur (:granule-ur granule)
                                                  :page-size 50}))))
      (assert-ingest-success #'ingest/delete-concept concept "Virtual-Product-Service")
      (index/wait-until-indexed)
      (is (= 0 (:hits (search/find-refs :granule {:granule-ur (:granule-ur granule)
                                                  :page-size 50})))))))
