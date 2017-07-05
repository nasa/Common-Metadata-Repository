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
        colored-items (vec (colorize-positional-order items-in-positional-order))]
    (if (not= (count test-concept-ids) (count result-ids))
      (do
        (println (ansi/style (format "%s ERROR - missing test data" description) :magenta))
        (doseq [concept-id test-concept-ids
                :let [result-position (.indexOf result-ids concept-id)]]
          (when (= -1 result-position)
            (println (ansi/style (format "...............Concept %s was not found in the result set"
                                         concept-id)
                                 :magenta)))))
      (if (= (sort items-in-positional-order) items-in-positional-order)
        (println (ansi/style (format "%s OK" description) :green))
        (println (ansi/style (str description " FAILED - out of order") :red)
                 colored-items)))))
