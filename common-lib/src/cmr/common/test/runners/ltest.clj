(ns cmr.common.test.runners.ltest
  "This test runner is a CMR adaptor for the ltest Clojure test runner."
  (:require
   [clojure.stacktrace :as stack]
   [clojure.string :as string]
   [clojure.test :as test]
   [cmr.common.test.runners.util :as util]
   [ltest.core :as ltest]
   [potemkin :refer [import-vars]]))

(import-vars
  [ltest.core
   run-test
   run-tests
   run-all-tests])

(def unit-test-suite
  {:name "Unit Tests"
   :nss (util/unit-test-namespaces)})

(def integration-test-suite
  {:name "Integration Tests"
   :nss (util/integration-test-namespaces)})

(def test-suites
  [unit-test-suite
   integration-test-suite])

(defn run-unit-tests
  []
  (ltest/run-suite unit-test-suite))

(defn run-integration-tests
  []
  (ltest/run-suite integration-test-suite))

(defn run-suites
  []
  (ltest/run-suites test-suites))
