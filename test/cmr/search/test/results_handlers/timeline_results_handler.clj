(ns cmr.search.test.results-handlers.timeline-results-handler
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [cmr.search.results-handlers.timeline-results-handler :as trh]
            [clj-time.core :as t]
            [clj-time.coerce :as c]))

; (def interval-time-gen
;   "TODO"
;   (gen/choose 1 100))

; (def granule-range-gen
;   "TODO"
;   (gen/fmap (fn [times]
;               (let [[start end] (sort times)]
;                 {:start start
;                  :end end}))
;             (gen/tuple interval-time-gen interval-time-gen)))

; (def granule-single-date-gen
;   "TODO"
;   (gen/fmap (fn [single-time]
;               {:start single-time
;                :end single-time})
;             interval-time-gen))

; (def granule-gen
;   (gen/one-of [granule-range-gen granule-single-date-gen]))

; (defn granules->start-end-events
;   [granules]
;   (->> granules
;        (mapcat (fn [{:keys [start end]}]
;                  [{:event-type :start :event-time start}
;                   {:event-type :end :event-time end}]))
;        (sort-by identity
;                 (fn [e1 e2]
;                   (let [{type1 :event-type time1 :event-time} e1
;                         {type2 :event-type time2 :event-time} e2
;                         result (compare time1 time2)]
;                     (cond
;                       (not= 0 result) result
;                       (= type1 type2) 0
;                       (= type1 :start) -1
;                       (= type2 :start) 1
;                       :else (throw (Exception. "Logic error"))))))))

; (defn granules->expected-intervals
;   [granules]
;   (let [empty-interval {:max-count 0 :curr-count 0 :start nil :end nil}]
;     (loop [current-interval empty-interval
;            events (granules->start-end-events granules)
;            intervals []]
;       (if (empty? events)
;         intervals
;         (let [{:keys [event-type event-time]} (first events)
;               current-interval (if (= event-type :start)
;                                  ;; start event
;                                  (-> current-interval
;                                      (update-in [:max-count] inc)
;                                      (update-in [:curr-count] inc)
;                                      (update-in [:start] #(or % event-time)))
;                                  ;; end event
;                                  (-> current-interval
;                                      (update-in [:curr-count] dec)
;                                      (assoc-in [:end] event-time)))
;               [current-interval intervals] (if (= (:curr-count current-interval) 0)
;                                              [empty-interval (conj intervals current-interval)]
;                                              [current-interval intervals])]
;           (recur current-interval (rest events) intervals))))))


; (defn within-range?
;   "Returns true if v is within min and max."
;   [^long v ^long min ^long max]
;   (and (>= v min)
;        (<= v max)))

; (defn intervals-intersect?
;   "Returns true if any of the intervals intersect."
;   [intervals]
;   (some identity
;         (for [{start1 :start end1 :end :as i1} intervals
;               {start2 :start end2 :end :as i2} intervals]
;           (and (not= i1 i2)
;                (or (within-range? start2 start1 end1)
;                    (within-range? end2 start1 end1))))))

; (defn interval->start-buckets
;   "TODO"
;   [interval]
;   )

; (defn intervals->start-buckets
;   "TODO"
;   [intervals]
;   )


; (defspec derive-intervals-spec 10000
;   (for-all [granules (gen/vector granule-gen)]
;     (let [expected-intervals (granules->expected-intervals granules)]
;       (and
;         (= (count granules) (reduce + (map :max-count expected-intervals)))
;         (not (intervals-intersect? expected-intervals)))

;       )

;     ))



(deftest adjacent-interval-test
  (testing "year"
    (are [y1 y2 adjacent?]
         (let [t1 (c/to-long (t/date-time y1))
               t2 (c/to-long (t/date-time y2))]
           (= adjacent?
              (trh/adjacent? :year {:end t1} {:start t2})))
         1999 2000 true
         2001 2002 true
         2001 2003 false))
  (testing "month"
    (are [y1 m1 y2 m2 adjacent?]
         (let [t1 (c/to-long (t/date-time y1 m1))
               t2 (c/to-long (t/date-time y2 m2))]
           (= adjacent?
              (trh/adjacent? :month {:end t1} {:start t2})))
         1999 12 2000 1 true
         2001 12 2002 1 true
         2000 1 2000 2 true
         2000 1 2001 2 false
         2000 4 2000 6 false
         2000 1 2003 2 false))
  (testing "day"
    (are [y1 m1 d1 y2 m2 d2 adjacent?]
         (let [t1 (c/to-long (t/date-time y1 m1 d1))
               t2 (c/to-long (t/date-time y2 m2 d2))]
           (= adjacent?
              (trh/adjacent? :day {:end t1} {:start t2})))
         1999 12 31 2000 1 1 true
         2001 12 31 2002 1 1 true
         2000 1 1 2000 1 2 true
         2000 1 31 2001 2 1 false
         2000 4 5 2000 4 7 false
         2000 1 1 2003 1 2 false))
  (testing "hour"
    (are [targs1 targs2 adjacent?]
         (let [t1 (c/to-long (apply t/date-time targs1))
               t2 (c/to-long (apply t/date-time targs2))]
           (= adjacent?
              (trh/adjacent? :hour {:end t1} {:start t2})))
         [1999 12 31 23] [2000 1 1 0] true
         [2001 12 31 23] [2002 1 1 0] true
         [2000 1 1 23] [2000 1 2 0] true
         [2000 1 31 23] [2001 2 1 0] false
         [2000 4 5 4] [2000 4 5 5] true
         [2000 4 5 4] [2000 4 5 6] false)))



(comment



  (def granules [{:start 1, :end 1} {:start 2, :end 2} {:start 2, :end 2}])

  (granules->start-end-events granules)
  (granules->expected-intervals granules)




)
















