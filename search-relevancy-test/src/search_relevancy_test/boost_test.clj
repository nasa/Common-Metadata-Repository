(ns search-relevancy-test.boost-test
 "Functions to run the boost tests"
 (:require
  [camel-snake-kebab.core :as csk]
  [search-relevancy-test.core :as core]
  [search-relevancy-test.relevancy-test :as relevancy-test]
  [search-relevancy-test.edsc-log-parser :as edsc-log-parser]
  [search-relevancy-test.reporter :as reporter]))

(def min-boost-value
  "Default boost value to start at when running tests"
  1.0)

(def max-boost-value
  "Default boost value to end at when running tests"
  4.0)

(def boost-increment
  "How much to increment the boost for each test"
  0.1)

(def result-separator
  "***********************************************")

(defn- create-search-params
  "Create additional search params to configure the field and boost in the s
  search request"
  [field value]
  (format "&boosts[%s]=%.2f&boosts[include_defaults]=true" field value))

(defn- create-test-args
  "Create logging args for test. Want to set log description so in local_test_runs.csv
  we can see what the field was set to when we compare results."
  [field value]
  ["-log-run-description"
   (format "%s %.2f" field value)
   "-log-history"
   "false"])

(defn- run-boost-test
  "Run the relevancy tests with the boost for the given field configured through
  search params. Return the result summary with the boost value assoc'd so we can
  reference it later"
  [filename field boost]
  (let [params (create-search-params field boost)] ; append to search request
    (println result-separator)
    (println (format "Running boost test with %s at %.2f" field boost))
    (-> (relevancy-test/run-anomaly-tests filename
                                          (create-test-args field boost)
                                          params)
        (reporter/generate-result-summary)
        (assoc (csk/->kebab-case-keyword field) boost))))

(defn boost-tests
  "Run relevancy tests over a range for a particular field boost to determine
  the lowest boost value that increases relevancy. Can specify the boost range or
  use defaults. Each test increments the boost by 0.1."
  ([filename field]
   (boost-tests field min-boost-value max-boost-value))
  ([filename field min-value max-value]
   (relevancy-test/test-setup :provider) ; Run the setup once
   (let [best-run (atom nil)]
     (doseq [x (range min-value (+ boost-increment max-value) boost-increment)
             :let [test-results (run-boost-test filename field x)]]
       (when (or (nil? @best-run) ; Save the earliest best run
                 (> (:average-dcg test-results)
                    (:average-dcg @best-run)))
         (reset! best-run test-results)))
     (println result-separator)
     (println (format "Boosts tests complete. Best run with %s at %.2f. DCG: %.3f"
                      field
                      (get @best-run (csk/->kebab-case-keyword field))
                      (:average-dcg @best-run))))))

(defn boost-tests-with-args
  "Parse arguments and run the boost tests. The -field argument is required."
  [args]
  (if-let [boost-field (core/get-argument-value args "-field")]
    (let [min-value (core/get-argument-value args "-min-value")
          min-value (or (when min-value (Double/parseDouble min-value))
                        min-boost-value)
          max-value (core/get-argument-value args "-max-value")
          max-value (or (when max-value (Double/parseDouble max-value))
                        max-boost-value)
          ;; Hardcoded to run the provider test suite
          filename core/provider-anomaly-filename]
      (boost-tests filename boost-field min-value max-value))
    (println "No field specified for boosts tests. Must specify a field to
             test boosts with the '-field' argument")))

(def boost-fields
  "All the fields that can be boosted"
  [:short-name
   :entry-id
   :project
   :platform
   :instrument
   :science-keywords
   :spatial-keyword
   :temporal-keyword
   :version-id
   :entry-title
   :provider
   :two-d-coord-name
   :processing-level-id
   :data-center])

(defn- create-boost-query-string
  "Creates the query string to use to pass in all of the boost-values to the CMR search API.
  boost-values - map with a key being the field name and value being the boost for that field."
  [boost-values]
  (reduce-kv (fn [orig-query-str field boost-value]
               (str orig-query-str (format "&boosts[%s]=%.2f" (name field) boost-value)))
             ""
             boost-values))

(defn- run-boost-test-all-fields
  "Runs a single test with random boost values between min-value and min-value + max-value."
  [filename boost-values]
  (let [query-string (create-boost-query-string boost-values)] ; append to search request
    (println result-separator)
    (println (format "Running boost test with boosts %s" boost-values))
    (-> (relevancy-test/run-anomaly-tests filename
                                          (create-test-args "fake" 3.14)
                                          query-string
                                          false)
        (reporter/generate-result-summary)
        (assoc :boosts boost-values))))

(defn random-boost-tests
  "Sets all of the boost values to random one decimal floats between the provided min-value and
  min-value + value-range. Runs num-iterations of the anomaly tests and tracks the best boost
  values to use based on the highest discounted cumulative gain."
  [min-value value-range num-iterations]
  (relevancy-test/test-setup :edsc) ; Run the setup once
  (let [best-run (atom nil)
        ;; Harcoded to always run the EDSC test for now
        filename core/edsc-anomaly-filename]
    (doseq [n (range num-iterations)
            :let [random-boosts (into {}
                                      (for [field boost-fields
                                            :let [random-value (+ min-value (rand value-range))]]
                                        [field (Float. (format "%.1f" random-value))]))
                  test-results (run-boost-test-all-fields filename random-boosts)
                  all-dcgs (conj (:dcgs @best-run) (max (get @best-run :average-dcg 0.0)
                                                        (get test-results :average-dcg 0)))]]
      (if (or (nil? (:average-dcg @best-run)) ; Save the earliest best run
              (> (:average-dcg test-results)
                 (:average-dcg @best-run)))
        (reset! best-run (assoc test-results :dcgs all-dcgs))
        (reset! best-run (assoc @best-run :dcgs all-dcgs))))
    (println result-separator)
    (println (format "Boosts tests complete. Best run %s, DCG: %.3f, Boosts: %s."
                    (:result-description @best-run)
                    (:average-dcg @best-run)
                    (:boosts @best-run)))
    @best-run))

(comment
 (def results (random-boost-tests 0.5 30.0 10))

 (run-boost-test-all-fields core/edsc-anomaly-filename {:short-name 0.9,
                                                        :science-keywords 1.2,
                                                        :entry-title 2.0,
                                                        :project 0.6})
 (run-boost-test-all-fields core/edsc-anomaly-filename {:short-name 1.1,
                                                        :instrument 2.7,
                                                        :version-id 1.7,
                                                        :science-keywords 1.2,
                                                        :data-center 2.9,
                                                        :project 0.7,
                                                        :spatial-keyword 0.5,
                                                        :entry-id 0.6,
                                                        :temporal-keyword 2.9,
                                                        :provider 2.1,
                                                        :entry-title 0.3,
                                                        :two-d-coord-name 2.7,
                                                        :platform 1.2,
                                                        :processing-level-id 2.8})

 (run-boost-test-all-fields core/edsc-anomaly-filename {:short-name 1.1, :instrument 2.7, :version-id 1.7, :science-keywords 1.2, :data-center 2.9, :project 0.7, :spatial-keyword 0.5, :entry-id 0.6, :temporal-keyword 2.9, :provider 2.1, :entry-title 2.6, :two-d-coord-name 2.7, :platform 1.2, :processing-level-id 2.8})
 (do
   (require '[proto-repl-charts.charts :as charts])
   (charts/line-chart "Best discounted cumulative gain by iteration"
                      {:Discounted_cumulative_gain (:dcgs results)}))
 (do
   (require '[criterium.core :as criterium])
   (criterium/with-progress-reporting
    (criterium/quick-bench
     (run-boost-test-all-fields {:short-name 0.7, :instrument 0.6, :version-id 4.6,
                                 :science-keywords 1.5, :data-center 4.5, :project 0.7,
                                 :spatial-keyword 1.2, :entry-id 3.1, :temporal-keyword 4.0,
                                 :provider 2.9, :entry-title 5.1, :two-d-coord-name 5.1,
                                 :platform 1.2, :processing-level-id 2.8}))))
 (run-boost-test-all-fields {:platform 1.2 :science-keywords 2.2 :instrument 0.95 :entry-title 2.2})
 (boost-tests-with-args ["-field" "entry-title" "-min-value" "1" "-max-value" "20"]))
