(ns cmr.system-int-test.utils.generic-util
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.data :as d]
   [clojure.data.xml :as x]
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

(def grid-good
  {:MetadataSpecification {:URL "https://cdn.earthdata.nasa.gov/generic/grid/v0.0.1"
                           :Name "Grid"
                           :Version "0.0.1"}
   :Name "Grid-A7-v1"
   :LongName "Grid A-7 version 1.0"
   :Version "v1.0"
   :Description "A sample grid"
   :GridDefinition {:CoordinateReferenceSystemID {:Type "EPSG:4326"
                                                  :URL "https://epsg.io/4326"}
                    :DimensionSize {:Height 3.14
                                    :Width 3.14
                                    :Time "12:00:00Z"
                                    :Other {:Name "Other Dimension Size",
                                            :Value "value here"}}
                    :Resolution {:Unit "km"
                                 :LongitudeResolution 64
                                 :LatitudeResolution 32}
                    :SpatialExtent {:BoundingRectangle {:0_360_DegreeProjection false,
                                                        :NorthBoundingCoordinate -90.0,
                                                        :EastBoundingCoordinate 180.0,
                                                        :SouthBoundingCoordinate 90.0,
                                                        :WestBoundingCoordinate -180.0}}}
   :Organization {:ShortName "nasa.gov"}
   :MetadataDate {:Create "2022-12-31T13:45:45Z"}
   :AdditionalAttribute {:Name "name"
                         :DataType "STRING"
                         :Description "something"}})

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token provider-id native-id] (generic-request token provider-id native-id nil :get))
  ([token provider-id native-id document method]
   (-> {:method method
        :url (url-helper/ingest-generic-crud-url provider-id native-id)
        :connection-manager (sys/conn-mgr)
        :body (when document (json/generate-string document))
        :throw-exceptions false
        :headers {transmit-config/token-header token}}
       (clj-http.client/request))))

(def generic-request-with-echo-token (partial generic-request (transmit-config/echo-system-token)))

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
