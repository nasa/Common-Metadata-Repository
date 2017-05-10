(ns cmr.dev-system.tests
  "A custom namespace for CMR development that allows more control over the running of tests than
  clojure.test/run-all-tests.

  Use the following keybinding in sublime

  { \"keys\": [\"alt+super+a\"], \"command\": \"run_command_in_repl\", \"args\": {\"command\": \"(def all-tests-future (future (cmr.dev-system.tests/run-all-tests {:fail-fast? true :speak? true} )))\", \"refresh_namespaces\": true}},
  "
  (:require
   [cmr.common.test.test-runner :as t]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]))

(defn system-integration-test?
  [test-ns]
  (re-find #"system-int-test" test-ns))

(defn integration-test-ns->compare-value
  "The comparator function to use for sorting integration tests. We want system integration tests to run last."
  [test-ns]
  (if (system-integration-test? test-ns)
    10
    1))

(defn run-all-tests
  "Runs all the tests in the cmr. It takes the same options as
  cmr.common.test-runner/run-all-tests."
  [options]
  (t/run-all-tests
    (assoc options
           :integration-test-namespaces (sort-by
                                         integration-test-ns->compare-value
                                         (t/integration-test-namespaces))
           :reset-fn dev-sys-util/reset)))
