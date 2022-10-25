(ns cmr.bootstrap.api.bulk-index
  "Defines the bulk index functions for the bootstrap API."
  (:require
   [cmr.bootstrap.api.messages :as msg]
   [cmr.bootstrap.api.util :as api-util]
   [cmr.bootstrap.services.bootstrap-service :as service]
   [cmr.common.date-time-parser :as date-time-parser]
   [cmr.common.services.errors :as errors]))

(defn index-provider
  "Index all the collections and granules for a given provider."
  [context provider-id-map params]
  (let [dispatcher (api-util/get-dispatcher context params :index-provider)
        provider-id (get provider-id-map "provider_id")
        start-index (Long/parseLong (get params :start_index "0"))
        result (service/index-provider
                context dispatcher provider-id start-index)]
    {:status 202
     :body {:message (msg/index-provider
                      params result provider-id start-index)}}))

(defn index-all-providers
  "Index all the collections and granules for all providers."
  [context params]
  (let [dispatcher (api-util/get-dispatcher context params :index-provider)]
    (service/index-all-providers context dispatcher)
    {:status 202
     :body {:message "Processing bulk indexing of all providers."}}))

(defn index-collection
  "Index all the granules in a collection"
  [context provider-id-collection-map params]
  (let [dispatcher (api-util/get-dispatcher context params :index-collection)
        provider-id (get provider-id-collection-map "provider_id")
        collection-id (get provider-id-collection-map "collection_id")
        start-index (Long/parseLong (get params :start_index "0"))
        _ (service/validate-collection context provider-id collection-id)
        result (service/index-collection
                context dispatcher provider-id collection-id {:start-index start-index})]
    {:status 202
     :body {:message (msg/index-collection params result collection-id)}}))

(defn data-later-than-date-time
  "Index all the data with a revision-date later than a given date-time."
  [context body params]
  (let [dispatcher (api-util/get-dispatcher context params :index-data-later-than-date-time)
        provider-ids (get body "provider_ids")
        date-time (:date_time params)]
    (if-let [date-time-value (date-time-parser/try-parse-datetime date-time)]
      {:status 202
       :body {:message (msg/data-later-than-date-time
                        params
                        (service/index-data-later-than-date-time
                         context dispatcher provider-ids date-time-value)
                        date-time)}}
      ;; Can't parse date-time.
      (errors/throw-service-error
       :invalid-data (msg/invalid-datetime date-time)))))

(defn index-system-concepts
  "Index all tags, acls, and access-groups."
  [context params]
  (let [dispatcher (api-util/get-dispatcher context params :index-system-concepts)
        start-index (or (:start-index params) 0)
        result (service/index-system-concepts context dispatcher start-index)]
    {:status 202
     :body {:message (msg/system-concepts params result)}}))

(defn index-concepts-by-id
  "Bulk index concepts of the given type for the given provider-id with the
  given concept-ids. The request-details-map should contain the following:
    provider-id  - \"the id of the provider for all the concepts\"
    concept_type - the concept type for all the concepts, e.g.,
                   \"granule\", \"collection\", etc.
    concept_ids  - a vector of concept ids."
  [context request-details-map params]
  (let [dispatcher (api-util/get-dispatcher context params :index-concepts-by-id)
        provider-id (get request-details-map "provider_id")
        concept-type (keyword (get request-details-map "concept_type"))
        concept-ids (get request-details-map "concept_ids")
        result (service/index-concepts-by-id
                context dispatcher provider-id concept-type concept-ids)]
    {:status 202
     :body {:message (msg/index-concepts-by-id params result)}}))

(defn index-variables
  "(Re-)Index the variables stored in metadata-db. If a provider-id is passed,
  only the variables for that provider will be indexed. With no provider-id,
  all providers' variables are (re-)indexed.

  Note that this function differs from the service function it calls, in that
  this function extracts dispatcher implementation from the context, while the
  service function takes the dispatcher as an argument."
  ([context params]
   (let [dispatcher (api-util/get-dispatcher context params :index-variables)
         result (service/index-variables context dispatcher)]
     {:status 202
      :body {:message (msg/index-variables params nil result)}}))
  ([context params provider-id]
   (let [dispatcher (api-util/get-dispatcher context params :index-variables)
         result (service/index-variables context dispatcher provider-id)]
     {:status 202
      :body {:message (msg/index-variables params provider-id result)}})))

(defn index-services
  "(Re-)Index the services stored in metadata-db. If a provider-id is passed,
  only the services for that provider will be indexed. With no provider-id,
  all providers' services are (re-)indexed.

  Note that this function differs from the service function it calls, in that
  this function extracts dispatcher implementation from the context, while the
  service function takes the dispatcher as an argument."
  ([context params]
   (let [dispatcher (api-util/get-dispatcher context params :index-services)
         result (service/index-services context dispatcher)]
     {:status 202
      :body {:message (msg/index-services params nil result)}}))
  ([context params provider-id]
   (let [dispatcher (api-util/get-dispatcher context params :index-services)
         result (service/index-services context dispatcher provider-id)]
     {:status 202
      :body {:message (msg/index-services params provider-id result)}})))

(defn index-tools
  "(Re-)Index the tools stored in metadata-db. If a provider-id is passed,
  only the tools for that provider will be indexed. With no provider-id,
  all providers' tools are (re-)indexed.

  Note that this function differs from the service function it calls, in that
  this function extracts dispatcher implementation from the context, while the
  service function takes the dispatcher as an argument."
  ([context params]
   (let [dispatcher (api-util/get-dispatcher context params :index-tools)
         result (service/index-tools context dispatcher)]
     {:status 202
      :body {:message (msg/index-tools params nil result)}}))
  ([context params provider-id]
   (let [dispatcher (api-util/get-dispatcher context params :index-tools)
         result (service/index-tools context dispatcher provider-id)]
     {:status 202
      :body {:message (msg/index-tools params provider-id result)}})))

(defn index-subscriptions
  "(Re-)Index the subscriptions stored in metadata-db. If a provider-id is passed,
  only the subscriptions for that provider will be indexed. With no provider-id,
  all providers' subscriptions are (re-)indexed.

  Note that this function differs from the service function it calls, in that
  this function extracts dispatcher implementation from the context, while the
  service function takes the dispatcher as an argument."
  ([context params]
   (let [dispatcher (api-util/get-dispatcher context params :index-subscriptions)
         result (service/index-subscriptions context dispatcher)]
     {:status 202
      :body {:message (msg/index-subscriptions params nil result)}}))
  ([context params provider-id]
   (let [dispatcher (api-util/get-dispatcher context params :index-subscriptions)
         result (service/index-subscriptions context dispatcher provider-id)]
     {:status 202
      :body {:message (msg/index-subscriptions params provider-id result)}})))

(defn index-generics
  "(Re-)Index the generic documents of a given type stored in metadata-db. If a provider-id is passed,
  only the documents for that provider will be indexed. With no provider-id,
  all providers' documents are (re-)indexed.

  Note that this function differs from the service function it calls, in that
  this function extracts dispatcher implementation from the context, while the
  service function takes the dispatcher as an argument."
  ([context params concept-type]
   (index-generics context params concept-type nil))
  ([context params concept-type provider-id]
   (let [dispatcher (api-util/get-dispatcher context params :index-generics)
         result (service/index-generics context dispatcher concept-type provider-id)]
     {:status 202
      :body {:message (msg/index-generics params concept-type provider-id result)}})))

(defn delete-concepts-by-id
  "Delete concepts from the indexes by concept-id."
  [context request-details-map params]
  (let [dispatcher (api-util/get-dispatcher context params :delete-concepts-from-index-by-id)
        provider-id (get request-details-map "provider_id")
        concept-type (keyword (get request-details-map "concept_type"))
        concept-ids (get request-details-map "concept_ids")
        result (service/delete-concepts-from-index-by-id
                context dispatcher provider-id concept-type concept-ids)]
    {:status 202
     :body {:message (msg/delete-concepts-by-id params result)}}))
