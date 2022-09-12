(ns cmr.system-int-test.utils.generic-util
  "Utility functions and definitions for use by generic document pipeline tests."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as client]
   [clojure.java.io :as jio]
   [clojure.test :refer :all]
   [cmr.system-int-test.system :as sys]
   [cmr.system-int-test.utils.url-helper :as url-helper]))

(def grid-good (-> "schemas/grid/v0.0.1/metadata.json"
                   (jio/resource)
                   (slurp)
                   (json/parse-string true)))

(defn generic-request
  "This function will make a request to one of the generic URLs using the provided
   provider and native id"
  ([provider-id native-id concept-type]
   (generic-request provider-id native-id concept-type nil :get))
  ([provider-id native-id concept-type document method]
   (-> {:method method
        :url (url-helper/ingest-generic-crud-url concept-type provider-id native-id)
        :connection-manager (sys/conn-mgr)
        :body (when document (json/generate-string document))
        :throw-exceptions false
        :headers {"Accept" "application/json"}}
       (client/request))))
