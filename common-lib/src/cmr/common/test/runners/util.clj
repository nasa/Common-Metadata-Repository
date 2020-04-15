(ns cmr.common.test.runners.util
  (:require
   [clojure.set :as set]
   [clojure.string :as string]))

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
       set
       (#(set/difference % (set (integration-test-namespaces))))
       vec
       sort))

(defn union-namespaces
  "Get a collection of combined unit and integration tests. This function is
  useful for auditing CMR tests, in particular, comparing with the independent
  results obtained from calling `(get-all-tests)`."
  []
  (set/union (integration-test-namespaces)
             (unit-test-namespaces)))

(defn- -all-cmr-namespaces
  "Returns the collection of all CMR namespaces, each as a namespace object."
  []
  (filter (fn [x] (string/starts-with? (ns-name x) "cmr")) (all-ns)))

(defn all-cmr-namespaces
  "Returns the collection of all CMR namespaces, each as a namespace string.
  This matches the return values of the `integration-test-namespaces` and
  `unit-test-namespaces` functions."
  []
  (filter (fn [x] (string/starts-with? x "cmr")) (map ns-name (all-ns))))

(defn get-all-tests
  "This function is useful for auditing the CMR tests. Given a collection of
  namespaces, it will search all of them for tests, returning all discovered
  tests as vars. If no collection of namespaces is passed as an arg, a list
  of all namepsace objects whose namepsace names beginn with 'cmr' will be
  used instead."
  ([]
   (get-all-tests (-all-cmr-namespaces)))
  ([nss]
   (->> nss
        (map #(vals (ns-interns %)))
        (flatten)
        (map #(-> % meta :test))
        (remove nil?))))
