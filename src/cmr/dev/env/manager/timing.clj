(ns cmr.dev.env.manager.timing)

(def times
  {:min (* 60 1000)
   :5mins (* 5 60 1000)
   :15mins (* 15 60 1000)
   :hr (* 60 60 1000)
   :6hrs (* 6 60 60 1000)
   :12hrs (* 12 60 60 1000)
   :day (* 24 60 60 1000)
   :week (* 7 24 60 60 1000)})

(defn new-tracker
  []
  (zipmap (keys times)
          (replicate (count times) (System/currentTimeMillis))))

(defn get-interval
  [times tracker time-key]
  (- (System/currentTimeMillis)
     (time-key tracker)
     (time-key times)))

(defn get-interval-key-pair
  [times tracker time-key]
  [time-key (get-interval times tracker time-key)])

(defn intervals
  [times tracker]
  (into {} (map (partial get-interval-key-pair times tracker) (keys times))))

(defn get-passed
  [times tracker time-key]
  [time-key (pos? (get-interval times tracker time-key))])

(defn passed?
  [times tracker]
  (into {} (map (partial get-passed times tracker) (keys times))))

(defn update-interval
  [times tracker time-key]
  (if (pos? (get-interval times tracker time-key))
    (assoc tracker time-key (System/currentTimeMillis))
    tracker))

(defn update-tracker
  [times tracker]
  (loop [[time-key & time-keys] (keys times)
         t tracker]
    (if-not time-key
      t
      (recur time-keys (update-interval times t time-key)))))
