(ns cmr-edsc-stubs.data.cmrapis
  "HTTP Service Operations

  Since these functions use the publicly accessible API endpoints for the
  published CMR services, they may be run from anywhere."
  (:require
   [cheshire.core :as json]
   [cmr-edsc-stubs.util :as util]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]
   [cmr.sample-data.core :as data-sources]))

(defn ingest-ges-disc-airx3std-collection
  ([]
    (ingest-ges-disc-airx3std-collection :local))
  ([deployment]
    (let [provider-id "GES_DISC"
          submit-content-type "application/echo10+xml"
          accept-content-type "application/json"
          native-id (str (java.util.UUID/randomUUID))
          client (ingest/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})]
      (ingest/create-collection client
                                provider-id
                                native-id
                                (data-sources/get-ges-disc-airx3std-collection)
                                {:content-type submit-content-type
                                 :accept accept-content-type}))))

(defn- ingest-variable-file
  [client provider-id var-file options]
  (println "Loading" (str var-file) "...")
  (let [native-id (str (java.util.UUID/randomUUID))]
    (ingest/create-variable client
                            provider-id
                            native-id
                            (slurp var-file)
                            options)))

(defn ingest-ges-disc-airx3std-variables
  ([]
    (ingest-ges-disc-airx3std-variables :local))
  ([deployment]
    (ingest-ges-disc-airx3std-variables
      (let [provider-id "GES_DISC"
            submit-content-type (str "application/vnd.nasa.cmr.umm+json;"
                                     "version=1.0; charset=UTF-8")
            accept-content-type "application/json"
            client (ingest/create-client {:endpoint deployment
                                          :token util/local-token
                                          :return-body? true})]
        (for [var-files [(data-sources/get-ges-disc-airx3std-ch4-variables)
                         ;; add more groups of variables here
                         ]]
          (for [var-file var-files]
            (ingest-variable-file
              client
              provider-id
              var-file
              {:content-type submit-content-type
               :accept accept-content-type}))))
      {:flatten? true}))
  ([data options]
    ;; This "utility arity" removes the extra nesting added by the top-level
    ;; `for` in the 1-arity form.
    (if (:flatten? options)
      (first data)
      data)))

(defn- associate-variable
  [client coll-id var-id options]
  (search/create-variable-association client
                                       var-id
                                       (json/generate-string
                                        [{:concept_id coll-id}])
                                      options))

(defn associate-ch4-variables-with-ges-disc-airx3std-collection
  ([collection-id variable-ids]
    (associate-ch4-variables-with-ges-disc-airx3std-collection
      :local collection-id variable-ids))
  ([deployment collection-id variable-ids]
    (let [provider-id "GES_DISC"
          submit-content-type "application/json"
          accept-content-type "application/json"
          client (search/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})
          options {:content-type submit-content-type
                   :accept accept-content-type}]
      (mapcat #(associate-variable client collection-id % options)
              variable-ids))))
