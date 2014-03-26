(ns cmr.indexer.data.metadata-db
  "Provide functions to invoke metadata db app"
  (:require [clj-http.client :as client]
            [cmr.common.services.errors :as errors]
            [cheshire.core :as cheshire]
            [cmr.system-trace.http :as ch]
            [cmr.system-trace.core :refer [deftracefn]]))

(def dummy-metadata
  "<Collection>
  <ShortName>DummyShortName</ShortName>
  <VersionId>DummyVersion</VersionId>
  <InsertTime>1999-12-31T19:00:00-05:00</InsertTime>
  <LastUpdate>1999-12-31T19:00:00-05:00</LastUpdate>
  <LongName>DummyLongName</LongName>
  <DataSetId>DummyDatasetId</DataSetId>
  <Description>A minimal valid collection</Description>
  <Orderable>true</Orderable>
  <Visible>true</Visible>
  </Collection>")

(defn endpoint
  "Returns the host and port of metadata db"
  []
  {:host "localhost"
   :port "3001"})

(deftracefn get-concept
  "Retrive the concept with the given concept and revision-id"
  [context concept-id revision-id]
  (let [{:keys [host port]} (endpoint)
        response (client/get (format "http://%s:%s/concepts/%s/%s" host port concept-id revision-id)
                             {:accept :json
                              :throw-exceptions false
                              :headers (ch/context->http-headers context)})
        status (:status response)]
    (if (= 200 status)
      (cheshire/decode (:body response))
      (errors/internal-error!
        (str "Failed to retrieve concept " concept-id "/" revision-id " from metadata-db: " (:body response))))))

