(ns cmr.system-int-test.utils.generic-util
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.data :as d]
   [clojure.data.xml :as x]
   [clojure.java.io :as jio]
   [clojure.string :as str]
   [clojure.test :refer :all]
   [cmr.acl.core :as acl]
   [cmr.common-app.config :as common-config]
   [cmr.common-app.test.side-api :as side]
   [cmr.common.mime-types :as mt]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.ingest.config :as ingest-config]
   [cmr.mock-echo.client.echo-util :as echo-util]
   [cmr.system-int-test.data2.provider-holdings :as ph]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.dev-system-util :as dev-sys-util]
   [cmr.system-int-test.utils.index-util :as index]
   [cmr.system-int-test.utils.metadata-db-util :as mdb]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.access-control :as ac]
   [cmr.transmit.config :as transmit-config]
   [cmr.umm-spec.versioning :as umm-versioning]
   [cmr.umm.echo10.echo10-collection :as c]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm.echo10.granule :as g])
  (:import
   [java.util UUID]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token concept-type provider-id native-id] (generic-request token concept-type provider-id native-id nil :get))
  ([token concept-type provider-id native-id document method]
   (-> {:method method
        :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
        :connection-manager (sys/conn-mgr)
        :body (when document (json/generate-string document))
        :throw-exceptions false
        :headers {"Accept" "application/json"
                  transmit-config/token-header token}}
       (clj-http.client/request))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; draft for eventually using with ingest-util/ingest-concept
(defn make-grid-concept
  [native-id]
  {:Info {:concept-id "GRD1200000001-PROV1"
          :native-id native-id
          :provider-id "PROV1"
          :format "application/vnd.nasa.cmr.umm+json;version=0.0.1"
          :revision-id 1
          :revision-date "2022-08-08T21:16:23Z"
          :created-at "2022-08-08T21:16:23Z"
          :user-id "ECHO_SYS"
          :concept-type "generic"
          :concept-sub-type "GRD"}
   :Metadata grid-good})
