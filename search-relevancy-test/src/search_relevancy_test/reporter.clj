(ns search-relevancy-test.reporter
  "Functions for generating reports from tests."
  (:require
   [clansi :as ansi]))

(defn positional-order
  "Returns the actual order of results based on the expected order of concept-ids and the
  actual order they were returned in. Orders are one based rather than zero based."
  [expected-ids actual-ids]
  (for [id actual-ids]
    (inc (.indexOf expected-ids id))))

(defn colorize-positional-order
  "Returns the item in green if it is in the correct positional-order and red if incorrect.
  The array passed in is expected to be 1 based - so [1 2 3] would all be in the correct order."
  [index-order]
  (map-indexed (fn [idx position]
                 (if (= (inc idx) position)
                   (ansi/style position :green)
                   (ansi/style position :red)))
               index-order))

(defn- result-discounted-cumulative-gain
 "Calculate the discounted cumulative gain for the results.
 The formula for DCG is: for each position i (starting at 1), calculate:
 (position relevancy / log2(i + 1))
 https://en.wikipedia.org/wiki/Discounted_cumulative_gain
 'position relevancy' is calculated using the inverse of the position because
 the lower the position the result came back at, the more relevant the result was."
 [result-positional-order]
 (apply +
        (map-indexed (fn [index result-position]
                       (if (pos? result-position)
                         ;; Inverse of position = relevancy for that position
                         (/ (- (inc (count result-positional-order)) result-position)
                            ;; 1-index the index and then add 1, take log base 2 of that
                            (/ (Math/log (+ 2 index)) (Math/log 2)))
                         0))
                     result-positional-order)))

(defn- test-discounted-cumulative-gain
  "Get discounted cumulative gain in percent form by dividing thd actual DCG by
  the ideal DCG"
  [result-positional-order num-test-concept-ids]
  (let [test-dcg (result-discounted-cumulative-gain result-positional-order)
        ideal-dcg (result-discounted-cumulative-gain (range 1 (inc num-test-concept-ids)))]
    (if (pos? ideal-dcg)
      (/ test-dcg ideal-dcg)
      0)))

(defn- reciprocal-rank
  "Return reciprocal rank for the item that is supposed to be in position 1.
  Calculated by 1 / actual position. "
  [result-positional-order]
  (let [idx (inc (.indexOf result-positional-order 1))]
    (if (pos? idx)
      (/ 1 idx)
      0)))

(defn analyze-search-results
  "Given the list of result concept ids in order and the order from the test,
  analyze the results to determine if the concept ids came back in the correct
  position. Print a message if not."
  [anomaly-test test-concept-ids result-ids]
  (let [description (format "...............Test %s: %s"
                            (:test anomaly-test)
                            (:description anomaly-test))
        items-in-positional-order (positional-order test-concept-ids result-ids)
        colored-items (vec (colorize-positional-order items-in-positional-order))]
    (if (not= (count test-concept-ids) (count result-ids))
      (do
        (println (ansi/style (format "%s ERROR - missing test data" description) :magenta))
        (doseq [concept-id test-concept-ids
                :let [result-position (.indexOf result-ids concept-id)]]
          (when (= -1 result-position)
            (println (ansi/style (format "...............Concept %s was not found in the result set"
                                         concept-id)
                                 :magenta))))
        (merge anomaly-test
               {:pass false
                :positional-order items-in-positional-order
                :result-description "Missing data"}))
      (do
        (let [pass (= (sort items-in-positional-order) items-in-positional-order)]
          (if pass
            (println (ansi/style (format "%s OK" description) :green))
            (println (ansi/style (str description " FAILED - out of order") :red)
                     colored-items))
          (merge anomaly-test
                 {:pass pass
                  :result-description (when (not pass) "Out of order")
                  :positional-order items-in-positional-order
                  :discounted-cumulative-gain (test-discounted-cumulative-gain items-in-positional-order
                                                                               (count test-concept-ids))
                  :reciprocal-rank (reciprocal-rank items-in-positional-order)}))))))

(defn- average
  "Return the average of results, given results is a collection of numbers"
  [results]
  (if (pos? (count results))
    (/ (apply + results) (count results))
    0))

(defn generate-result-summary
  "Calculate fields for the result summary"
  [results]
  (let [failed-results (filter #(= false (:pass %)) results)]
    {:result-description (format "%s tests passed out of %s"
                                 (count (filter #(= true (:pass %)) results))
                                 (count results))
     :pass (empty? failed-results)
     :average-dcg (average (remove nil? (map :discounted-cumulative-gain results)))
     :average-failed-dcg (average (remove nil? (map :discounted-cumulative-gain failed-results)))
     :mean-reciprocal-rank (double (average (remove nil? (map :reciprocal-rank results))))}))

(defn print-result-summary
  "Create and print the results summary"
  [results]
  (let [result-summary (generate-result-summary results)]
    (println "---------------------------------------")
    (println "SUMMARY: ")
    (println (:result-description result-summary))
    (println (format "Average discounted cumulative gain: %.3f"
                     (:average-dcg result-summary)))
    (println (format "Average discounted cumulative gain for failed tests: %.3f"
                     (:average-failed-dcg result-summary)))
    (println (format "Mean reciprocal rank: %.3f"
                     (:mean-reciprocal-rank result-summary)))))
