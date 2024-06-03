(ns cmr.common.test.runners.ltest
  "This test runner is a CMR adaptor for the ltest Clojure test runner."
  (:require
   [cmr.common.test.runners.util :as util]
   [ltest.core :as ltest]
   [potemkin :refer [import-vars]]))

(import-vars
  [ltest.core
   run-test
   run-tests
   run-all-tests])

(def unit-test-suite
  "Setting for unit test"
  {:name "Unit Tests"
   :nss (util/unit-test-namespaces)})

(def integration-test-suite
  "Settings for intergration tests"
  {:name "Integration Tests"
   :nss (util/integration-test-namespaces)})

(def test-suites
  "List of tests suits to do"
  [unit-test-suite
   integration-test-suite])

(defn run-unit-tests
  "Command for running unit tests"
  []
  (ltest/run-suite unit-test-suite))

(defn run-integration-tests
  "Command for running int tests"
  []
  (ltest/run-suite integration-test-suite))

(defn run-suites
  "Do it all"
  []
  (ltest/run-suites test-suites))
