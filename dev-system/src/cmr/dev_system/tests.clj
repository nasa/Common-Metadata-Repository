(ns cmr.dev-system.tests
  "A custom namespace for CMR development that allows more control over the running of tests than
  clojure.test/run-all-tests.

  Use the following keybinding in sublime

  { \"keys\": [\"alt+super+a\"], \"command\": \"run_command_in_repl\", \"args\": {\"command\": \"(def all-tests-future (future (cmr.dev-system.tests/run-all-tests {:fail-fast? true :speak? true} )))\", \"refresh_namespaces\": true}},
  "
  (:require [clojure.test :as t]
            [cmr.common.util :as u]
            [cmr.system-int-test.utils.ingest-util :as ingest-util]
            [cmr.common.dev.util :as du]
            [clojure.set :as set]))

(defn system-integration-test?
  [test-ns]
  (re-find #"system-int-test" test-ns))

(defn integration-test-ns->compare-value
  "The comparator function to use for sorting integration tests. We want system integration tests
  to run last."
  [test-ns]
  (if (system-integration-test? test-ns)
    10
    1))

(def integration-test-namespaces
  "The list of integration test namespaces. Anything that contains 'cmr.' and 'int-test' is
  considered an integration test namespace."
  (->> (all-ns)
       (map str)
       (filter #(re-find #"cmr\..*int-test" %))
       (sort-by integration-test-ns->compare-value)))

(def unit-test-namespaces
  "This defines a list of unit test namespaaces. Anything namespace name that contains 'cmr.' and
  'test' that is not an integration test namespace is considered a unit test namespace."
  (set/difference
    (->> (all-ns)
         (map str)
         (filter #(re-find #"cmr\..*test" %))
         set)
    (set integration-test-namespaces)))

(defn run-tests
  "Runs all the tests matching the list of namespace regular expressions. The tests are run
  in parallel if parallel? is true. Returns a lazy sequence of test results maps."
  [namespaces parallel?]
  (let [map-fn (if parallel? pmap map)]
    (map-fn (fn [test-ns]
              (taoensso.timbre/set-level! :warn)
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
     :slowest-tests slowest-tests}))

(defn print-results
  "Displays the analyzed tests results. Accepts options speak? to indicate if a successful result
  should be spoken audibly."
  [analyzed-results {:keys [speak?]}]
  (let [{:keys [num-tests
                num-assertions
                num-failing
                num-error
                slowest-tests]} analyzed-results
        success? (= 0 (+ num-failing num-error))]
    (when speak?
      (if success?
        (du/speak "Success")
        (du/speak "Failure")))

    (println "-------------------------------------------------------------------")
    (println "Slowest Tests:")
    (doseq [{:keys [took test-ns]} slowest-tests]
      (println (str test-ns) "-" took))
    (println)
    (printf "Total Took: %s\n" (:took analyzed-results))
    (printf "Total: Ran %d tests containing %d assertions.\n" num-tests num-assertions)
    (printf "Total: %d failures, %d errors.\n" num-failing num-error)
    (println "-------------------------------------------------------------------")))

(defn failed-test-result?
  "Returns true if the test result was not successful."
  [test-result]
  (or (> (:fail test-result) 0)
      (> (:error test-result) 0)))

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

(defn run-all-tests
  "Runs all the tests in the cmr.
  Options:
   - :speak? - set to true to speak 'success' or 'failure' after tests complete.
   - :fail-fast? - set to true to fail after the first failed test."
  [options]
  (println "-------------------------------------------------------------------")
  (println "RUNNING ALL TESTS")
  (let [{:keys [fail-fast?]} options
        test-results-handler (fail-fast?->test-results-handler fail-fast?)
        unittest-results (run-tests unit-test-namespaces true)
        inttest-results (run-tests integration-test-namespaces false)
        [took test-results] (u/time-execution
                              (test-results-handler
                                (concat unittest-results inttest-results)))]
    (ingest-util/reset)
    (print-results (assoc (analyze-results test-results)
                          :took took)
                   options)))


(comment
  (run-tests unit-test-ns-matchers)

  (t/run-all-tests #"cmr.system-int-test.*")


    (def all-tests-future (future (run-all-tests {:fail-fast? true :speak? true})))

  (deref all-tests-future)

  (print-results (analyze-results (second @all-tests-future)) {})


  )
