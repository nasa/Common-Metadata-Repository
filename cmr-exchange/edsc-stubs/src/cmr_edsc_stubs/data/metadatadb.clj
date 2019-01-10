(ns cmr-edsc-stubs.data.metadatadb
  "Metadata DB Operations

  The CMR metadata-db is only accessibly via the localhost, so the intent
  with these functions is that they be used against a local instance of
  the CMR or be run directly on the systems where they are deployed."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [cmr-edsc-stubs.util :as util]
   [cmr.client.common.util :as client-util]
   [cmr.sample-data.core :as data-sources]))

(defn create-provider
  ([provider-data]
    (create-provider provider-data :local :metadata-db))
  ([provider-data environment-type service-key]
    (let [endpoint (client-util/get-endpoint environment-type service-key)]
      (-> {:body provider-data
           :content-type :json
           :throw-exceptions false
           :headers util/local-token-header}
          ((partial client/post (format "%s/providers" endpoint)))
          (select-keys [:status :body])
          (update-in [:body] #(json/parse-string % true))))))

(defn create-ges-disc-provider
  []
  (create-provider (data-sources/get-ges-disc-provider)))

