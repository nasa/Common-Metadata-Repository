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
  (float (- (now) start)))

(defn most-frequent
  "This identifies the most frequently occuring data in a collection
  and returns it."
  [data]
  (->> data
       frequencies
       ;; the 'frequencies' function puts data first; let's swap the order
       (map (fn [[k v]] [v k]))
       ;; sort in reverse order to get the highest counts first
       (sort (comp - compare))
       ;; just get the highest
       first
       ;; the first element is the count, the second is the bounding data
       second))

(defn promise?
  [p]
  (isa? (class p) clojure.lang.IPending))
