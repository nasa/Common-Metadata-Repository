(ns cmr.opendap.util)

(defn bool
  [arg]
  (if (contains? #{true :true "true" "TRUE" "t" "T" 1} arg)
    true
    false))

(defn remove-empty
  [coll]
  (remove #(or (nil? %) (empty? %)) coll))

(defn deep-merge
  "Merge maps recursively."
  [& maps]
  (if (every? #(or (map? %) (nil? %)) maps)
    (apply merge-with deep-merge maps)
    (last maps)))

(defn now
  []
  (/ (System/currentTimeMillis) 1000))

(defn timed
  [start]
  (- (now) start))
