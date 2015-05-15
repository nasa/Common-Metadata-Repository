(ns cmr.system-int-test.ingest.provider-cmr-only-client-id-test
  "CMR provider CMR-ONLY flag and client id integration tests"
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(def ingest-functions-to-test
  [#'ingest/validate-concept #'ingest/ingest-concept #'ingest/delete-concept])

(def cmr-only-true-errors
  ["Provider PROV1 was configured as CMR Only which only allows ingest directly through the CMR. It appears from the client id that it was sent from ECHO."])

(def cmr-only-false-errors
  ["Provider PROV1 was configured as false for CMR Only which only allows ingest indirectly through ECHO. It appears from the client id [bad] that ingest was not sent from ECHO."])

(defn- assert-ingest-result
  "Executes the given function with the concept and client-id and compares the result with
  the expected ones. It will print out the executed function name when test fails."
  [func-var concept client-id expected-status expected-errors]
  (let [{:keys [status errors]} ((var-get func-var) concept {:client-id client-id})]
    (is (= [expected-status expected-errors] [status errors]) (format "Failed in %s." func-var))))

(deftest collection-cmr-only-client-id-test
  (testing "validation on CMR-ONLY provider cannot be submitted by client Echo"
    (let [coll1 (d/ingest "PROV1" (dc/collection))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (doseq [func ingest-functions-to-test]
        (assert-ingest-result func concept "Echo" 400 cmr-only-true-errors)
        (assert-ingest-result func concept "any" 200 nil))))
  (testing "validation on non CMR-ONLY provider must be submitted by client Echo"
    (let [coll1 (d/ingest "PROV1" (dc/collection))
          concept (dissoc (d/item->concept coll1) :revision-id)]
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-result func concept "bad" 400 cmr-only-false-errors)
        (assert-ingest-result func concept "Echo" 200 nil)))))

(deftest granule-cmr-only-client-id-test
  (testing "validation on CMR-ONLY provider cannot be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          concept (d/item->concept (dg/granule collection))]
      (ingest/ingest-concept concept)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-result func concept "Echo" 400 cmr-only-true-errors)
        (assert-ingest-result func concept "any" 200 nil))))
  (testing "validation on non CMR-ONLY provider must be submitted by client Echo"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          concept (d/item->concept (dg/granule collection))]
      (ingest/ingest-concept concept)
      (ingest/update-ingest-provider "PROV1" false)
      (ingest/clear-caches)
      (doseq [func ingest-functions-to-test]
        (assert-ingest-result func concept "bad" 400 cmr-only-false-errors)
        (assert-ingest-result func concept "Echo" 200 nil)))))

