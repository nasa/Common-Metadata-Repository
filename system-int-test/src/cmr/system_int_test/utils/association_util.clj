(ns cmr.system-int-test.utils.association-util
  "This contains utilities for testing associations."
  (:require
   [clojure.test :refer [is]]
   [cmr.system-int-test.system :as s]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.ingest-util :as ingest-util]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.transmit.association :as transmit-assoc]
   [cmr.transmit.generic-association :as transmit-generic-assoc]
   [cmr.transmit.echo.tokens :as tokens]
   [cmr.umm-spec.versioning :as versioning]))

(defn generic-associate-by-concept-ids-revision-ids
  "Associates a concept with the given concept id, revision id to a list of concept ids and revision ids."
  ([token concept-id revision-id concept-id-revision-id-list]
   (generic-associate-by-concept-ids-revision-ids token concept-id revision-id concept-id-revision-id-list nil))
  ([token concept-id revision-id concept-id-revision-id-list options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-generic-assoc/associate-concept
                   (s/context) concept-id revision-id concept-id-revision-id-list options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

(defn generic-dissociate-by-concept-ids-revision-ids
  "Disassociates a concept with the given concept id, revision id to a list of concept ids and revision ids."
  ([token concept-id revision-id concept-id-revision-id-list]
   (generic-dissociate-by-concept-ids-revision-ids token concept-id revision-id concept-id-revision-id-list nil))
  ([token concept-id revision-id concept-id-revision-id-list options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-generic-assoc/dissociate-concept
                   (s/context) concept-id revision-id concept-id-revision-id-list options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

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

(defn associate-by-single-concept-id
  "Associates a variable with a collection. This uses the single variable/collection association route."
  ([token concept-id coll-concept-id]
   (associate-by-single-concept-id token concept-id coll-concept-id nil))
  ([token concept-id coll-concept-id options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-assoc/associate-single-concept
                    (s/context) concept-id coll-concept-id options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

(defn dissociate-by-single-concept-id
  "Dissociates a variable from a collection. This uses the single variable/collection dissociation route."
  ([token concept-id coll-concept-id]
   (dissociate-by-single-concept-id token concept-id coll-concept-id nil))
  ([token concept-id coll-concept-id options]
   (let [options (merge {:raw? true :token token} options)
         response (transmit-assoc/dissociate-single-concept
                    (s/context) concept-id coll-concept-id options)]
     (index/wait-until-indexed)
     (ingest-util/parse-map-response response))))

(defn assert-invalid-data-error
  "Assert association response when status code is 422 is correct"
  [expected-errors response]
  (let [{:keys [status body errors]} response]
    (is (= [422 (set expected-errors)]
           [status (set errors)]))))

(defn make-service-association
  "Returns the service association for associating the given service and collections"
  [token concept-id coll-concept-ids]
  (let [response (associate-by-concept-ids token concept-id coll-concept-ids)]
    (-> response
        :body
        first
        :service-association)))

(defn make-tool-association
  "Returns the tool association for associating the given tool and collections"
  [token concept-id coll-concept-ids]
  (let [response (associate-by-concept-ids token concept-id coll-concept-ids)]
    (-> response
        :body
        first
        :tool-association)))
