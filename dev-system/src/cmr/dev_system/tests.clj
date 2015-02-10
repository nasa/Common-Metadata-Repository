(ns cmr.dev-system.tests
  "A custom namespace for CMR development that allows more control over the running of tests than
  clojure.test/run-all-tests.

  Use the following keybinding in sublime

  { \"keys\": [\"alt+super+a\"], \"command\": \"run_command_in_repl\", \"args\": {\"command\": \"(def all-tests-future (future (cmr.dev-system.tests/run-all-tests {:fail-fast? true :speak? true} )))\", \"refresh_namespaces\": true}},
  "
  (:require [clojure.test :as t]
            [cmr.common.util :as u]
            [cmr.system-int-test.utils.ingest-util :as ingest-util]
            [cmr.common.dev.util :as du]))

(def unit-test-names
  "Lists the set of unit tests that will run. This will be combined with a suffix and prefix to
  create a regular expression to match namespace names."
  [:acl
   :common
   :index-set
   :indexer
   :metadata-db
   :search
   :spatial
   :system-trace
   :transmit
   :umm])

(def unit-test-ns-matchers
  "Converted list of unit test names into namespace matching regular expressions"
  (->> unit-test-names
       (map name)
       (map #(str "cmr." % ".test.*"))
       (map re-pattern)))

(def integration-namespace-suffixes
  "A set of suffixes matching integration test namespaces."
  [:index-set.int-test
   :metadata-db.int-test
   :system-int-test])

(def integration-test-ns-matchers
  "Converted list of integration test names into namespace matching regular expressions"
  (->> integration-namespace-suffixes
       (map name)
       (map #(str "cmr." % ".*"))
       (map re-pattern)))

(defn ns-matchers->namespaces
  "Finds all the namespaces matching the set of regular expressions passed in."
  [ns-matchers]
  (for [ns-matcher ns-matchers
        test-ns (filter #(re-matches ns-matcher (name (ns-name %))) (all-ns))]
    test-ns))

(defn run-tests
  "Runs all the tests matching the list of namespace regular expressions. The tests are run
  in parallel if parallel? is true. Returns a lazy sequence of test results maps."
  [ns-matchers parallel?]
  (let [map-fn (if parallel? pmap map)]
    (map-fn (fn [test-ns]
              (taoensso.timbre/set-level! :warn)
              (let [[millis results] (u/time-execution (t/run-tests test-ns))]
                (assoc results
                       :took millis
                       :test-ns test-ns)))
            (ns-matchers->namespaces ns-matchers))))

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
        unittest-results (run-tests unit-test-ns-matchers true)
        inttest-results (run-tests integration-test-ns-matchers false)
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
