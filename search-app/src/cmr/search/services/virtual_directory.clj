(ns cmr.search.services.virtual-directory
  "Service functions for performing virtual directory queries."
  (:require
   [cheshire.core :as json]
   [clj-time.core :as clj-time]
   [clojure.set :as set]
   [clojure.string :as string]
   [cmr.common-app.services.search.group-query-conditions :as gc]
   [cmr.common-app.services.search.params :as common-params]
   [cmr.common-app.services.search.query-execution :as qe]
   [cmr.common-app.services.search.query-model :as qm]
   [cmr.common-app.services.search.query-to-elastic :as q2e]
   [cmr.common.date-time-parser :as parser]
   [cmr.common.util :as util]
   [cmr.transmit.connection :as conn]))

(defmulti parse-date
  "Returns the value from the date string that matches the provided interval.
  Example: (parse-interval \"2017-01-01T00:00:00+0000\" :year) returns 2017."
  (fn [datetime interval]
    interval))

(defmethod parse-date :year
  [datetime interval]
  (second (re-find #"^(\d{4})-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :month
  [datetime interval]
  (second (re-find #"^\d{4}-(\d{2})-\d{2}T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :day
  [datetime interval]
  (second (re-find #"^\d{4}-\d{2}-(\d{2})T\d{2}:\d{2}:\d{2}\+\d+$" datetime)))

(defmethod parse-date :hour
  [datetime interval]
  (second (re-find #"^\d{4}-\d{2}-\d{2}T(\d{2}):\d{2}:\d{2}\+\d+$" datetime)))

(defn- base-url
  "The root URL for executing a collection search against the CMR."
  [context]
  (let [public-search-config (set/rename-keys (get-in context [:system :public-conf])
                                              {:relative-root-url :context})]
    (format "%s/directories" (conn/root-url public-search-config))))

(defn- level->str
  "Generates a string representing the current level"
  [level]
  (format "%s%s%s"
          (if (:year level) (str (:year level) "/") "")
          (if (:month level) (str (:month level) "/") "")
          (if (:day level) (str (:day level) "/") "")))

(defn- remove-trailing-slash
  "Removes trailing slash from a string."
  [str]
  (if (string/ends-with? str "/")
    (subs str 0 (dec (.length str)))
    str))

(defn build-link
  "Builds a link to navigate to the next level in the virtual directory structure."
  [context concept-id value current-level]
  (remove-trailing-slash (format "%s/%s/%s%s" (base-url context) concept-id (level->str current-level) value)))

(defn build-remove-link
  "Builds a remove link."
  [context concept-id current-level]
  (remove-trailing-slash (format "%s/%s/%s" (base-url context) concept-id (level->str current-level))))

(defn build-group-node
  "Builds a group node."
  [title children]
  {:title title
   :type :group
   :children children})

(defn build-filter-node
  "Builds a filter node."
  [title children]
  {:title title
   :type :filter
   :children children})

(defn build-title-type-links-to-remove
  "Build the title type and links to remove for the parent node."
  [context concept-id value children link-level]
  {:title value
   :type :filter
   :links {:remove (build-remove-link context concept-id link-level)}
   :children children})

(defn build-group-and-filter-node
  "Builds a group node with a filter node as a child."
  [context concept-id title parent-value children link-level]
  (let [group-node [(build-group-node title children)]
        parent-value-node (build-title-type-links-to-remove context concept-id parent-value
                                                            group-node link-level)]
    parent-value-node))

(defn time-ranges->clj-time-interval
  "Returns the next interval based on the values in the map."
  [time-ranges]
  (let [all-keys (set (keys time-ranges))]
    (if-not (contains? all-keys :year)
      nil
      (if-not (contains? all-keys :month)
        (clj-time/years 1)
        (if-not (contains? all-keys :day)
          (clj-time/months 1)
          (if-not (contains? all-keys :hour)
            (clj-time/days 1)
            (clj-time/hours 1)))))))

(defn time-ranges->next-interval
  "Returns the next interval based on the values in the map."
  [time-ranges]
  (let [all-keys (set (keys time-ranges))]
    (if-not (contains? all-keys :year)
      :year
      (if-not (contains? all-keys :month)
        :month
        (if-not (contains? all-keys :day)
          :day
          (when-not (contains? all-keys :hour)
            :hour))))))

(def num-urls
  "Number of download URLs to return"
  100)

(defn- build-top-level-response
  "Builds the final JSON response."
  [context concept-id {:keys [year month day hour] :as time-ranges} children download-urls]
  (let [interval-granularity (time-ranges->next-interval time-ranges)
        hour-node (if (= :hour interval-granularity)
                    (build-group-and-filter-node context concept-id "Hour" day children
                                                 (select-keys time-ranges [:year :month]))
                    (if (nil? interval-granularity)
                      (build-group-and-filter-node context concept-id "Hour" day
                                                   [(util/remove-nil-keys
                                                     (build-title-type-links-to-remove
                                                      context concept-id hour nil
                                                      (select-keys time-ranges [:year :month :day])))]
                                                   (select-keys time-ranges [:year :month]))
                      (when hour (util/remove-nil-keys (build-group-node "Hour" children)))))
        day-node (if (= :day interval-granularity)
                   (build-group-and-filter-node context concept-id "Day" month children
                                                (select-keys time-ranges [:year]))
                   (when day (util/remove-nil-keys (build-group-and-filter-node context concept-id "Day" month [hour-node]
                                                                                (select-keys time-ranges [:year])))))
        month-node (if (= :month interval-granularity)
                     (build-group-and-filter-node context concept-id "Month" year children
                                                  {})
                     (when month (util/remove-nil-keys
                                  (build-group-and-filter-node context concept-id "Month" year [day-node]
                                                               {}))))
        year-node (if (= :year interval-granularity)
                    (build-group-node "Year" children)
                    (when year (util/remove-nil-keys
                                (build-group-node "Year" [month-node]))))
        download-urls-map {:download_urls (seq download-urls)
                           :has_more_urls (>= (count download-urls) num-urls)}]
    (util/remove-nil-keys (merge (build-group-node "Temporal" [year-node]) download-urls-map))))

(defn build-response
  "Parses the Elasticsearch aggregations response to return a map of the years for the given
  collection as well as the number of granules for that year."
  [context response concept-id interval current-level]
  (let [download-urls (keep #(:href (json/parse-string (:atom-links %) true))
                            (:items response))
        buckets (get-in response [:aggregations :start-date-intervals :buckets])
        value-maps (map (fn [bucket]
                          (let [value (parse-date (:key_as_string bucket) interval)]
                            {:title value
                             :type :filter
                             :count (:doc_count bucket)
                             :links {:apply (build-link context concept-id value current-level)}}))
                        buckets)]
    (build-top-level-response context concept-id current-level (sort-by :title value-maps) download-urls)))

(defn get-date-ranges
  "Returns a tuple of start date and end date based on the passed in time range map."
  [{:keys [year month day hour] :as time-ranges}]
  (let [start-date-string (when year
                            (format "%s-%s-%sT%s:00:00.000Z"
                                    year (or month "01") (or day "01") (or hour "00")))
        start-date (when start-date-string (parser/parse-datetime start-date-string))
        interval (time-ranges->clj-time-interval time-ranges)
        end-date (when start-date
                   (clj-time/minus (clj-time/plus start-date interval) (clj-time/millis 1)))]
    [start-date end-date]))

(defn get-directories-by-collection
  "Returns a map containing all of the years for the given collection as well as the number of
  granules for that year."
  [context concept-id time-ranges]
  (let [;query-condition (qm/string-condition :collection-concept-id concept-id)
        collection-condition (common-params/parameter->condition
                              context :granule :collection-concept-id
                              [concept-id] nil)
        [start-date end-date] (get-date-ranges time-ranges)
        temporal-condition (when (and start-date end-date)
                             (qm/map->DateRangeCondition {:field :start-date
                                                          :start-date start-date
                                                          :end-date end-date}))
        query-condition (if temporal-condition
                          (gc/and-conds [temporal-condition collection-condition])
                          collection-condition)
        interval-granularity (time-ranges->next-interval time-ranges)
        aggregations (when interval-granularity
                      {:start-date-intervals
                       {:date_histogram
                        {:field (q2e/query-field->elastic-field :start-date :granule)
                         :interval interval-granularity}}
                       :end-date-intervals
                        {:date_histogram
                         {:field (q2e/query-field->elastic-field :end-date :granule)
                          :interval interval-granularity}}})
        query (qm/query {:concept-type :granule
                         :condition query-condition
                         :page-size num-urls
                         :result-fields [:atom-links]
                         :aggregations aggregations
                         :result-format :query-specified})
                        ;  :skip-acls? skip-acls?
        results (qe/execute-query context query)]
    (build-response context results concept-id interval-granularity time-ranges)))
