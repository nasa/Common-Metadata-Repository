(ns cmr.search.services.acls.acl-results-handler-helper
  "This contains functions for aiding result handlers in retrieving and returning sets of results
  for in ways that can be filtered for ACLs. When we execute queries with only concept ids and the
  results can be built directly from Elasticsearch we post process the ACL filtering on the list of
  concepts that is returned. This contains the functions to get the right data out of elastic and
  format the results so that the ACL filtering can be applied."
  (:require [cmr.common.date-time-parser :as dtp]
            [clojure.string :as str]
            [cmr.common.util :as u]
            [cmr.umm.umm-core :as ummc]))

(def collection-elastic-fields
  "These are the fields that must be retrieved from Elasticsearch to enforce ACLs"
  [:provider-id
   :entry-title
   :access-value
   :start-date
   :end-date])

(def granule-elastic-fields
  "These are the fields that must be retrieved from Elasticsearch to enforce ACLs"
  [:provider-id
   :collection-concept-id
   :access-value
   :start-date
   :end-date])

(defn parse-elastic-datetime
  "Parses a date time string received from Elasticsearch"
  [dts]
  (when dts
    (dtp/parse-datetime (str/replace dts "+0000" "Z"))))

(defmulti parse-elastic-item
  "Parses the Elasticsearch response into a map that can be used to enforce ACLs."
  (fn [concept-type elastic-result]
    concept-type))

(defmethod parse-elastic-item :collection
  [concept-type elastic-result]
  (let [{{[entry-title] :entry-title
          [provider-id] :provider-id
          [access-value] :access-value
          [start-date] :start-date
          [end-date] :end-date} :fields} elastic-result
        start-date (parse-elastic-datetime start-date)
        end-date (parse-elastic-datetime end-date)]

    (-> {:concept-type concept-type
         :provider-id provider-id
         :EntryTitle entry-title}
        (u/lazy-assoc :AccessConstraints {:Value access-value})
        (u/lazy-assoc :TemporalExtents
                      (let [start-date (parse-elastic-datetime start-date)
                            end-date (parse-elastic-datetime end-date)]
                        [{:RangeDateTimes (when start-date [{:BeginningDateTime start-date}
                                                            :EndingDateTime end-date])}])))))

(defmethod parse-elastic-item :granule
  [concept-type elastic-result]
  (let [{{[provider-id] :provider-id
          [access-value] :access-value
          [collection-concept-id] :collection-concept-id
          [start-date] :start-date
          [end-date] :end-date} :fields} elastic-result]
    (-> {:concept-type concept-type
         :provider-id provider-id
         :collection-concept-id collection-concept-id}
        (u/lazy-assoc :access-value access-value)
        (u/lazy-assoc
          :temporal
          (let [start-date (parse-elastic-datetime start-date)
                end-date (parse-elastic-datetime end-date)]
            (when start-date {:range-date-time {:beginning-date-time start-date
                                                :ending-date-time end-date}}))))))
