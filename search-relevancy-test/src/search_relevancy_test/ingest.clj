(ns search-relevancy-test.ingest
 (:require
  [clj-http.client :as client]
  [clojure.java.io :as io]
  [clojure.string :as string]
  [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
  [cmr.system-int-test.utils.index-util :as index]
  [cmr.system-int-test.utils.ingest-util :as ingest-util]
  [cmr.transmit.config :as transmit-config]))

(def community-usage-path
  "http://localhost:3003/community-usage-metrics")

(defn ingest-community-usage-metrics
  "Ingest community usage metrics from the csv file"
  []
  (client/put
    community-usage-path
    {:throw-exceptions false
     :body (slurp (io/resource "community_usage_metrics.csv"))
     :content-type "text/csv"
     :headers {transmit-config/token-header (transmit-config/echo-system-token)}}))

(defn- provider-guids
  "Return a list of guid/provider pairs given a list of provider strings"
  [providers]
  (apply merge
    (for [provider providers]
      {(str "provguid" provider) provider})))

(defn create-providers
 "Reset and create a provider for each provider found in the test files"
 [test-files]
 (dev-sys-util/reset)
 (let [providers (keys (group-by :provider test-files))
       provider-guids (provider-guids providers)]
   (ingest-util/setup-providers provider-guids)))

(defn ingest-test-files
  "Given a map of file, format, provider, and concept-id, ingest each file"
  [test-files]
  (doseq [test-file test-files
          :let [{:keys [file concept-id provider format]} test-file]]
    (-> (ingest-util/concept :collection provider concept-id format (slurp file))
        (assoc :concept-id concept-id)
        ingest-util/ingest-concept))
  (index/wait-until-indexed))
