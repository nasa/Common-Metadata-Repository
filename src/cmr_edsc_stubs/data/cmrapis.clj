(ns cmr-edsc-stubs.data.cmrapis
  "HTTP Service Operations

  Since these functions use the publicly accessible API endpoints for the
  published CMR services, they may be run from anywhere."
  (:require
   [cheshire.core :as json]
   [cmr-edsc-stubs.data.sources :as data-sources]
   [cmr-edsc-stubs.util :as util]
   [cmr.client.ingest :as ingest]
   [cmr.client.search :as search]))

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

(defn ingest-ges-disc-airx3std-variables
  ([]
    (ingest-ges-disc-airx3std-variables :local))
  ([deployment]
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
          (do
            (println "Loading" (str var-file) "...")
            (let [native-id (str (java.util.UUID/randomUUID))]
              (ingest/create-variable client
                                      provider-id
                                      native-id
                                      (slurp var-file)
                                      {:content-type submit-content-type
                                       :accept accept-content-type}))))))))

(defn associate-ch4-variables-with-ges-disc-airx3std-collection
  ([]
    (associate-ch4-variables-with-ges-disc-airx3std-collection :local))
  ([deployment]
    (let [provider-id "GES_DISC"
          submit-content-type "application/json"
          accept-content-type "application/json"
          client (search/create-client {:endpoint deployment
                                        :token util/local-token
                                        :return-body? true})
          vars (search/get-variables client
                                     {:provider provider-id
                                      :page_size 1000}
                                     {:accept accept-content-type})
          cols (search/get-collections client
                                       {:provider provider-id}
                                       {:accept accept-content-type})
          collection-id (:id (get-in cols [:feed :entry 0]))]
      (for [var-id (map :concept_id (:items vars))]
        (search/create-variable-association client
                                            var-id
                                            (json/generate-string
                                             [{:concept_id collection-id}])
                                            {:content-type submit-content-type
                                             :accept accept-content-type})))))
