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
 'position relevancy' is how relevant the result was, so higher is better. When
 the position comes back lower is better so take the inverse of the position to use
 in the calculations"
 [result-positional-order]
 (apply +
        (map-indexed (fn [index result-position]
                       (if (> result-position 0)
                         ;; Inverse of positio = relevancy for that position
                         (/ (- (inc (count result-positional-order)) result-position)
                            ;; 1-index the index and the add 1, take log base 2 of that
                            (/ (Math/log (+ 2 index)) (Math/log 2)))
                         0))
                     result-positional-order)))

(defn- test-discounted-cumulative-gain
  "Get discounted cumulative gain in percent form by dividing thd actual DCG by
  the ideal DCG"
  [result-positional-order num-test-concept-ids]
  (let [test-dcg (result-discounted-cumulative-gain result-positional-order)
        ideal-dcg (result-discounted-cumulative-gain (range 1 (inc num-test-concept-ids)))]
    (/ test-dcg ideal-dcg)))

(defn analyze-search-results
  "Given the list of result concept ids in order and the order from the test,
  analyze the results to determine if the concept ids came back in the correct
  position. Print a message if not."
  [anomaly-test test-concept-ids result-ids]
  (let [atom-fail-count (atom 0)
        description (format "...............Test %s: %s"
                            (:test anomaly-test)
                            (:description anomaly-test))
        items-in-positional-order (positional-order test-concept-ids result-ids)
        colored-items (vec (colorize-positional-order items-in-positional-order))
        dcg (test-discounted-cumulative-gain items-in-positional-order (count test-concept-ids))]
    (if (not= (count test-concept-ids) (count result-ids))
      (do
        (println (ansi/style (format "%s ERROR - missing test data" description) :magenta))
        (doseq [concept-id test-concept-ids
                :let [result-position (.indexOf result-ids concept-id)]]
          (when (= -1 result-position)
            (println (ansi/style (format "...............Concept %s was not found in the result set"
                                         concept-id)
                                 :magenta))))
        {:pass false})
      (do
        (if (= (sort items-in-positional-order) items-in-positional-order)
          (println (ansi/style (format "%s OK" description) :green))
          (println (ansi/style (str description " FAILED - out of order") :red)
                   colored-items))
        {:pass (= (sort items-in-positional-order) items-in-positional-order)
         :discounted-cumulative-gain dcg}))))

(defn- average
  "Return the average of results, given results is a collection of numbers"
  [results]
  (/ (apply + results) (count results)))

(defn print-result-summary
  "Create and print the results summary"
  [results]
  (let [results (flatten results)
        discounted-cumulative-gain (remove nil? (map :discounted-cumulative-gain results))
        failed-dcg (remove nil? (map :discounted-cumulative-gain (filter #(= false (:pass %)) results)))]
    (println "SUMMARY: ")
    (println (format "%s tests passed out of %s"
                     (count (filter #(= true (:pass %)) results))
                     (count results)))
    (println (format "Average discounted cumulative gain: %.2f"
                     (average discounted-cumulative-gain)))
    (println (format "Average discounted cumulative gain for failed tests: %.2f"
                     (average failed-dcg)))))
