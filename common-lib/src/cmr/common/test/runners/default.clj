(ns cmr.common.test.runners.default
  "A custom namespace for CMR development that allows more control over the
  running of tests than clojure.test/run-all-tests.

  Use the following keybinding in sublime

  {\"keys\": [\"alt+super+a\"],
   \"command\": \"run_command_in_repl\",
   \"args\": {\"command\": \"(def all-tests-future (future (cmr.common.test.runners.default/run-all-tests {:fail-fast? true :speak? true} )))\",
              \"refresh_namespaces\": true}},

  Note that this functionality was originally provided in the `cmr.common.test.test-runner`
  namespace."
  (:require
   [clojure.set :as set]
   [clojure.test :as t]
   [cmr.common.config :refer [defconfig]]
   [cmr.common.dev.util :as du]
   [cmr.common.test.runners.util]
   [cmr.common.util :as u]
   [potemkin :refer [import-vars]]))

(import-vars
  [cmr.common.test.runners.util
   integration-test-namespaces
   unit-test-namespaces])

(defconfig unit-test-parallel
  "Indicates whether to run unit tests in parallel. Default to false for dev system to run stable."
  {:type Boolean
   :default false})

(defn run-tests
  "Runs all the tests matching the list of namespace regular expressions. The tests are run
  in parallel if parallel? is true. Returns a lazy sequence of test results maps."
  [namespaces parallel?]
  (let [map-fn (if parallel? pmap map)]
    (map-fn (fn [test-ns]
              (let [[millis results] (u/time-execution (t/run-tests (find-ns (symbol test-ns))))]
                (assoc results
                       :took millis
                       :test-ns test-ns)))
            namespaces)))

(defn analyze-results
  "Analyzes a list of tests results to compute totals, find slowest tests, etc. Returns a map of
  computed test data."
  [test-results]
  (let [num-tests (apply + (map :test test-results))
        num-assertions (apply + (map :pass test-results))
        num-failing (apply + (map :fail test-results))
        num-error (apply + (map :error test-results))
        slowest-tests (->> test-results
                           (sort-by :took)
                           reverse
                           (map #(select-keys % [:took :test-ns]))
                           (take 20))]
    {:num-tests num-tests
     :num-assertions num-assertions
     :num-failing num-failing
     :num-error num-error
     :slowest-tests slowest-tests
     :took (/ (reduce + 0 (map :took test-results)) 1000.0)}))

(defn print-results
  "Displays the analyzed tests results. Accepts options speak? to indicate if a successful result
  should be spoken audibly."
  [analyzed-results & {:keys [speak?]}]
  (let [{:keys [num-tests
                num-assertions
                num-failing
                num-error
                slowest-tests]} analyzed-results
        success? (zero? (+ num-failing num-error))]
    (when speak?
      (if success?
        (du/speak "Samantha" "Success")
        (du/speak "Daniel" "Test Failure")))

    (println "-------------------------------------------------------------------")
    (println "Slowest Tests:")
    (doseq [{:keys [took test-ns]} slowest-tests]
      (println (str test-ns) "-" took))
    (println)
    (printf "Total Took: %ss\n" (:took analyzed-results))
    (printf "Total: Ran %d tests containing %d assertions.\n" num-tests num-assertions)
    (printf "Total: %d failures, %d errors.\n" num-failing num-error)
    (println "-------------------------------------------------------------------")))

(defn failed-test-result?
  "Returns true if the test result was not successful."
  [test-result]
  (or (pos? (:fail test-result))
      (pos? (:error test-result))))

(defn fail-fast?->test-results-handler
  "Returns a test results handler based on the value of fail-fast?.
  Test results are lazy so something needs to force their execution. If fail-fast? is true the
  tests will be stopped after the first file that has errors."
  [fail-fast?]
  (if fail-fast?
    (fn [test-results]
      (reduce (fn [test-results test-result]
                (if (failed-test-result? test-result)
                  (do
                    (println "FAILING FAST")
                    (reduced (conj test-results test-result)))
                  (conj test-results test-result)))
              []
              test-results))
    doall))

(def last-test-results
  (atom nil))

(defn run-all-tests
  "Runs all the tests in the cmr.
  Options:
   * :speak? - set to true to speak 'success' or 'failure' after tests complete.
   * :fail-fast? - set to true to fail after the first failed test.
   * :reset-fn - a function to call to perform a reset or wait for completion after tests are finished
     before printing results.
   * :unit-test-namespaces - Allows overriding which unit test namespaces to test
   * :integration-test-namespaces - Allows overriding which integration test namespaces to test"
  [options]
  (println "-------------------------------------------------------------------")
  (println "RUNNING ALL TESTS")
  (let [{:keys [fail-fast? reset-fn]} options
        unit-test-namespaces (get options :unit-test-namespaces (unit-test-namespaces))
        integration-test-namespaces (get options :integration-test-namespaces
                                         (integration-test-namespaces))

        test-results-handler (fail-fast?->test-results-handler fail-fast?)
        unittest-results (run-tests unit-test-namespaces (unit-test-parallel))
        inttest-results (run-tests integration-test-namespaces false)
        [took test-results] (u/time-execution
                              (test-results-handler
                                (concat unittest-results inttest-results)))]
    (reset! last-test-results test-results)
    (when reset-fn (reset-fn))
    (print-results (assoc (analyze-results test-results)
                          :took took)
                   options)))
