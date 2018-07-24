(ns cmr.opendap.results.common)

(defn get-results
  [data plural-key singular-key]
  (or (plural-key data)
      (when-let [result (singular-key data)]
        [result])))

(defn get-filtered-results
  [data plural-key singular-key]
  (->> data
       (mapcat #(get-results % plural-key singular-key))
       (remove nil?)
       vec))

(defn collect-results
  [coll plural-key singular-key]
  (let [results (get-filtered-results coll plural-key singular-key)]
    (when (seq results)
      {plural-key results})))
