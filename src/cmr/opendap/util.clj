(ns cmr.opendap.util)

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
