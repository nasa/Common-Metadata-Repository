(ns cmr.search.results-handlers.timeline-results-handler
  "Handles granule timeline interval results. The timeline is implemented by finding aggregations
  over granule start date and end dates. The aggregation results of start date and end dates are
  converted to a list of events of some number of granules starting and some number of granules
  ending. The events counts of granules are used to determine when intervals start and stop."
  (:require
   [cheshire.core :as json]
   [clj-time.coerce :as c]
   [clj-time.core :as t]
   [cmr.common-app.services.search :as qs]
   [cmr.common-app.services.search.elastic-results-to-query-results :as elastic-results]
   [cmr.common-app.services.search.elastic-search-index :as elastic-search-index]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.query-execution :as query-execution]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common-app.services.search.results-model :as r]
   [cmr.common.services.errors :as errors]
   [cmr.search.models.query :as q]))

(defn- query-aggregations
  "Returns the elasticsearch aggregations to put in the query for finding granule timeline intervals."
  [interval-granularity]
  {:by-collection
   {:terms {:field (q2e/query-field->elastic-field :collection-concept-id :granule)
            ;; See CMR-1577
            :size 10000}
    :aggregations {:start-date-intervals
                   {:date_histogram
                    {:field (q2e/query-field->elastic-field :start-date :granule)
                     :min_doc_count 1
                     :interval interval-granularity}}
                   :end-date-intervals
                    {:date_histogram
                     {:field (q2e/query-field->elastic-field :end-date :granule)
                      :min_doc_count 1
                      :interval interval-granularity}}}}})

(defmethod query-execution/pre-process-query-result-feature :timeline
  [context query feature]
  (let [{:keys [interval]} query
        temporal-cond (q/map->TemporalCondition (select-keys query [:start-date :end-date]))]
    (-> query
        (assoc :aggregations (query-aggregations interval)
               :page-size 0
               :sort-keys nil)
        (update-in [:condition] #(gc/and-conds [% temporal-cond])))))


(defmethod elastic-search-index/concept-type+result-format->fields [:granule :timeline]
  [concept-type query]
  ;; Timeline results are aggregation results so we select no fields
  [])

(defn event-comparator
  "Sorts events by event time. If event times match a start event comes before an end event."
  [e1 e2]
  (let [{type1 :event-type time1 :event-time} e1
        {type2 :event-type time2 :event-time} e2
        result (compare time1 time2)]
    (cond
      (not= 0 result) result
      (= type1 type2) 0
      (= type1 :start) -1
      (= type2 :start) 1
      :else (throw (Exception. "Logic error")))))

(defn- interval-bucket->event
  "Converts an interval bucket from the elastic search response into an event. An event has an
  event type (:start or :end), hits, and event time that the event occurred. "
  [event-type bucket]
  {:event-type event-type
   :hits (:doc_count bucket)
   :event-time (c/from-long (long (:key bucket)))})

(defn collection-bucket->ordered-events
  "Returns a list of ordered events within the collection bucket."
  [collection-bucket]
  (sort-by identity event-comparator
           (concat
             (map (partial interval-bucket->event :start)
                  (get-in collection-bucket [:start-date-intervals :buckets]))
             (map (partial interval-bucket->event :end)
                  (get-in collection-bucket [:end-date-intervals :buckets])))))

(def initial-interval
  "An empty intervals"
  {:start nil
   :end nil
   :curr-count 0
   :num-grans 0})

(defn ordered-events->intervals
  "Walks over the ordered events and returns intervals of continuous granule data."
  [events]
  (loop [current-interval initial-interval
         events-left events
         intervals []]
    (if (empty? events-left)
      (if (not= current-interval initial-interval)
        ;; There was one or more granules that border the edge of the interval or didn't have end dates
        (conj intervals (assoc current-interval :no-end true))
        intervals)
      (let [{:keys [event-type hits event-time]} (first events-left)
            ;; Process the next event
            current-interval (if (= event-type :start)
                               ;; A start event
                               (-> current-interval
                                   (update-in [:curr-count] #(+ % hits))
                                   (update-in [:num-grans] #(+ % hits))
                                   (update-in [:start] #(or % event-time)))

                               ;; An end event
                               (-> current-interval
                                   (update-in [:curr-count] #(- % hits))
                                   (assoc :end event-time)))
            ;; Check to see if the current interval has ended
            [current-interval intervals] (if (= (:curr-count current-interval) 0)
                                           ;; Has ended
                                           [initial-interval (conj intervals current-interval)]
                                           ;; Not ended
                                           [current-interval intervals])]
        (recur current-interval (rest events-left) intervals)))))

(def interval-granularity->dist-fn
  "Maps an interval granularity to a clj-time function to compute the distance between two time values
  in that granularity."
  {:year t/in-years
   :month t/in-months
   :day t/in-days
   :hour t/in-hours
   :minute t/in-minutes
   :second t/in-seconds})

(defn adjacent?
  "Returns true if 2 intervals are adjacent based on the interval granularity. The order here is
  important. It assumes i2 follows i1."
  [interval-granularity i1 i2]
  (let [dist-fn (interval-granularity->dist-fn interval-granularity)]
    (= 1 (dist-fn (t/interval (:end i1) (:start i2))))))

(defn- merge-intervals
  "Merges two intervals together. The start date of the first is taken along with the end date of
  the second. The hits are added together."
  [i1 i2]
  (-> i1
      (update-in [:num-grans] #(+ % (:num-grans i2)))
      (assoc :end (:end i2))))

(defn- merge-adjacent-intervals
  "Takes an interval-granularity and a list of intervals. Merges the intervals together that are
  within one interval granularity from each other. ASsumes intervals are already sorted by start
  date."
  [interval-granularity intervals]
  (loop [intervals intervals new-intervals [] prev nil]
    (if (empty? intervals)
      (conj new-intervals prev)
      (let [current (first intervals)
            [new-intervals prev] (cond
                                   ;; first interval
                                   (nil? prev)
                                   [new-intervals current]

                                   (adjacent? interval-granularity prev current)
                                   ;; Previous and current are adjacent. Merge them together
                                   [new-intervals (merge-intervals prev current)]

                                   :else
                                   ;; Normal case. Add prev to new-intervals.
                                   ;; current becomes the new prev
                                   [(conj new-intervals prev) current])]
        (recur (rest intervals) new-intervals prev)))))

(def ^:private interval-granularity->period-fn
  "Maps interval granularity types to the clj-time period functions"
  {:year t/years
   :month t/months
   :day t/days
   :hour t/hours
   :minute t/minutes
   :second t/seconds})


(defn- advance-interval-end-date
  "Advances the interval end date by one unit of interval granularity. The end dates coming back from
  elasticsearch are at the beginning of the interval. The granules end date falls somewhere between
  the start of the interval and the end of it. By advancing the end date to the end we indicate to
  clients that there is data within that time area."
  [interval-granularity interval]
  (let [one-unit ((interval-granularity->period-fn interval-granularity) 1)]
    (update-in interval [:end] #(when % (t/plus % one-unit)))))

(defn- constrain-interval-to-user-range
  "Constrains the start and end date of the interval to within the range given by the user"
  [start-date end-date interval]
  ;; This flag indicates in the ordered-events->intervals function that the interval had extra
  ;; granules that flowed over past the end of the last interval. This means we need to extend the
  ;; interval end date to the end of the range the user requested.
  (let [no-end (:no-end interval)]
    (-> interval
        (update-in [:start] #(if (t/before? % start-date) start-date %))
        (update-in [:end] #(if (or no-end (nil? %) (t/after? % end-date)) end-date %)))))

(defn- collection-bucket->intervals
  "Takes a single collection bucket from the aggregation response and returns the intervals
  for that collection."
  [interval-granularity start-date end-date collection-bucket]
  (let [collection-concept-id (:key collection-bucket)
        num-granules (:doc_count collection-bucket)
        intervals (->> collection-bucket
                       collection-bucket->ordered-events
                       ordered-events->intervals)
        interval-sum (reduce + (map :num-grans intervals))]
    (when (not= num-granules interval-sum)
      (errors/internal-error!
        (format "The sum of intervals, %s, did not match the count in the collection bucket, %s"
                interval-sum num-granules)))
    {:concept-id collection-concept-id
     :intervals (->> intervals
                     (merge-adjacent-intervals interval-granularity)
                     (map (partial advance-interval-end-date interval-granularity))
                     (map (partial constrain-interval-to-user-range start-date end-date)))}))

(defmethod elastic-results/elastic-results->query-results [:granule :timeline]
  [context query elastic-results]
  (let [{:keys [start-date end-date interval]} query
        items (map (partial collection-bucket->intervals (:interval query) start-date end-date)
                   (get-in elastic-results [:aggregations :by-collection :buckets]))]
    (r/map->Results {:items items
                     :timed-out (:timed_out elastic-results)
                     :result-format (:result-format query)})))

(defn interval->response-tuple
  "Converts an interval into the response tuple containing the start, end, and number of granules."
  [query {:keys [start end num-grans]}]
  [(/ (c/to-long start) 1000)
   (-> end
       ;; End may not be set if the granule didn't have an end date
       (or (:end-date query))
       c/to-long
       (/ 1000))
   num-grans])

(defn collection-result->response-result
  "Convers the collection interval map into the timeline response."
  [query coll-result]
  (update-in coll-result [:intervals] (partial map (partial interval->response-tuple query))))

(defmethod qs/search-results->response [:granule :timeline]
  [context query results]
  (let [{:keys [items]} results
        response (map (partial collection-result->response-result query) items)]
    (json/generate-string response)))

(comment

  (defn prettify-results
    [{:keys [items]}]
    (for [{:keys [concept-id intervals]} items]
      {:concept-id concept-id
       :intervals
       (for [{:keys [start end num-grans]} intervals]
         {:start (str start)
          :end (str end)
          :num-grans num-grans})}))

  (prettify-results
    (elastic-results/elastic-results->query-results
      nil {:result-format :timeline
           :interval :year} @last-elastic-results)))
