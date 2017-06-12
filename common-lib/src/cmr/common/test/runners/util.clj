(ns cmr.common.test.runners.util
  (:require
   [clojure.set :as set]))

(defn integration-test-namespaces
  "The list of integration test namespaces. Anything that contains 'cmr.' and 'int-test' is
  considered an integration test namespace. This must be a function instead of a var because of its
  use of all-ns. It must be executed right before test execution to find all test namespaces."
  []
  (->> (all-ns)
       (map str)
       (filter #(re-find #"cmr\..*int-test" %))
       vec
       sort))

(defn unit-test-namespaces
  "This defines a list of unit test namespaaces. Anything namespace name that contains 'cmr.' and
  'test' that is not an integration test namespace is considered a unit test namespace. This must be
  a function instead of a var because of its use of all-ns. It must be executed right before test
  execution to find all test namespaces."
  []
  (->> (all-ns)
       (map str)
       (filter #(re-find #"cmr\..*test" %))
       set
       (#(set/difference % (set (integration-test-namespaces))))
       vec
       sort))
