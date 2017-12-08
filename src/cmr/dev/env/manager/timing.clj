(ns cmr.dev.env.manager.timing)

(def default-intervals
  {:30sec (* 30 1000)
   :min (* 60 1000)
   :5min (* 5 60 1000)
   :15min (* 15 60 1000)
   :hr (* 60 60 1000)
   :6hr (* 6 60 60 1000)
   :12hr (* 12 60 60 1000)
   :day (* 24 60 60 1000)
   :week (* 7 24 60 60 1000)})

(defn new-tracker
  [intervals]
  (zipmap (keys intervals)
          (replicate (count intervals) (System/currentTimeMillis))))

(defn get-interval
  [intervals tracker time-key]
  (- (System/currentTimeMillis)
     (time-key tracker)
     (time-key intervals)))

(defn make-interval-kv
  ([intervals tracker time-key]
    (make-interval-kv intervals tracker identity time-key))
  ([intervals tracker interval-fn time-key]
    [time-key (interval-fn (get-interval intervals tracker time-key))]))

(defn do-intervals
  [intervals tracker interval-fn]
  (->> (keys intervals)
       (map (partial make-interval-kv intervals tracker interval-fn))
       (into {})))

(defn intervals
  [intervals tracker]
  (do-intervals intervals tracker identity))

(defn passed?
  [intervals tracker]
  (do-intervals intervals tracker pos?))

(defn update-interval
  ([intervals tracker time-key]
    (update-interval intervals tracker time-key (constantly true)))
  ([intervals tracker time-key update-fn]
    (if (pos? (get-interval intervals tracker time-key))
      (do
        (update-fn intervals tracker time-key)
        (assoc tracker time-key (System/currentTimeMillis)))
      tracker)))

(defn update-tracker
  ([intervals tracker]
    (update-tracker intervals tracker (constantly true)))
  ([intervals tracker update-fn]
    (loop [[time-key & time-keys] (keys intervals)
           t tracker]
      (if-not time-key
        t
        (recur time-keys (update-interval intervals t time-key update-fn))))))
