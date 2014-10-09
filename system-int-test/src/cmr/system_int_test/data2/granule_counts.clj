(ns cmr.system-int-test.data2.granule-counts
  "Contains utilities for checking granule counts results."
  (:require [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.data2.atom :as da]))


(defmulti granule-counts-match?
  "Takes a map of collections to counts and actual results and checks that the references
  were found and that the granule counts are correct."
  (fn [result-format expected-counts result]
    result-format))

(defmethod granule-counts-match? :xml
  [result-format expected-counts refs-result]
  (let [count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:entry-title coll) granule-count]))
        actual-count-map (into {} (for [{:keys [name granule-count]} (:refs refs-result)]
                                    [name granule-count]))
        refs-match? (d/refs-match? (keys expected-counts) refs-result)
        counts-match? (= count-map actual-count-map)]
    (when-not refs-match?
      (println "Expected:" (pr-str (map :entry-title (keys expected-counts))))
      (println "Actual:" (pr-str (map :name (:refs refs-result)))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and refs-match? counts-match?)))

(defmethod granule-counts-match? :echo10
  [result-format expected-counts results]
  (let [items (:items results)
        count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:concept-id coll) granule-count]))
        actual-count-map (into {} (for [{:keys [concept-id granule-count]} items]
                                    [concept-id granule-count]))
        results-match? (d/metadata-results-match? :echo10 (keys expected-counts) results)
        counts-match? (= count-map actual-count-map)]
    (when-not results-match?
      (println "Expected:" (pr-str (map :concept-id (keys expected-counts))))
      (println "Actual:" (pr-str (map :concept-id items))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and results-match? counts-match?)))

(defmethod granule-counts-match? :atom
  [result-format expected-counts atom-results]
  (let [entries (get-in atom-results [:results :entries])
        count-map (into {} (for [[coll granule-count] expected-counts]
                             [(:entry-title coll) granule-count]))
        actual-count-map (into {} (for [{:keys [dataset-id granule-count]} entries]
                                    [dataset-id granule-count]))
        results-match? (da/atom-collection-results-match?
                         (keys expected-counts) atom-results)
        counts-match? (= count-map actual-count-map)]
    (when-not results-match?
      (println "Expected:" (pr-str (map :entry-title (keys expected-counts))))
      (println "Actual:" (pr-str (map :dataset-id entries))))
    (when-not counts-match?
      (println "Expected:" (pr-str count-map))
      (println "Actual:" (pr-str actual-count-map)))
    (and results-match? counts-match?)))


(defmulti results->actual-has-granules
  "Converts the results into a map of collection ids to the has-granules value"
  (fn [result-format results]
    result-format))

(defmethod results->actual-has-granules :xml
  [result-format results]
  (into {} (for [{:keys [id has-granules]} (:refs results)]
             [id has-granules])))

(defmethod results->actual-has-granules :echo10
  [result-format results]
  (into {} (for [{:keys [concept-id has-granules]} (:items results)]
             [concept-id has-granules])))

(defmethod results->actual-has-granules :atom
  [result-format results]
  (into {} (for [{:keys [id has-granules]} (get-in results [:results :entries])]
             [id has-granules])))