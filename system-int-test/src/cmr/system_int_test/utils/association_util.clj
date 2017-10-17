(ns cmr.system-int-test.utils.association-util
  "This contains utilities for testing associations."
  (:require
   [clojure.test :refer [is]]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.search-util :as search]
   [cmr.transmit.association :as transmit-assoc]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.umm-spec.versioning :as versioning]))

(defn associate-by-concept-ids
  "Associates a variable/service with the given concept id to a list of collections."
  ([token concept-id coll-concept-ids]
   (associate-by-concept-ids token concept-id coll-concept-ids nil))
  ([token concept-id coll-concept-ids options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-assoc/associate-concept
                   (s/context) concept-id coll-concept-ids options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

(defn dissociate-by-concept-ids
  "Dissociates a variable/service with the given concept id from a list of collections."
  ([token concept-id coll-concept-ids]
   (dissociate-by-concept-ids token concept-id coll-concept-ids nil))
  ([token concept-id coll-concept-ids options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-assoc/dissociate-concept
                   (s/context) concept-id coll-concept-ids options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

(defn assert-invalid-data-error
  "Assert association response when status code is 422 is correct"
  [expected-errors response]
  (let [{:keys [status body errors]} response]
    (is (= [422 (set expected-errors)]
           [status (set errors)]))))
