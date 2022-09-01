(ns cmr.system-int-test.utils.generic-util
  "Utility functions and definitions for use by generic document pipeline tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.url-helper :as url-helper]
   [cmr.transmit.config :as transmit-config]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([token provider-id native-id concept-type]
   (generic-request token provider-id native-id concept-type nil :get))
  ([token provider-id native-id concept-type document method]
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
