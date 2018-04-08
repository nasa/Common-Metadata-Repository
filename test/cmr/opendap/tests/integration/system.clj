(ns ^:integration cmr.opendap.tests.integration.system
  "Tests for ensuring the proper setup of a testing system.

  Note: this namespace is exclusively for integration tests; all tests defined
  here will use one or more integration test fixtures."
  (:require
    [clojure.test :refer :all]
    [cmr.opendap.testing.system :as test-system]))

(use-fixtures :once test-system/with-system)

(deftest system-type-check
 (is (record? @test-system/*mgr*)))
