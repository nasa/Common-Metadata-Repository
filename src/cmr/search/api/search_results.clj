(ns cmr.search.api.search-results
  "Contains functions for validating search results requested formats and for converting to
  requested format"
  (:require [cheshire.core :as json]
            [pantomime.media :as mt]
            [cmr.common.services.errors :as errors]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [clojure.data.csv :as csv]
            [cmr.search.models.results :as r])
  (:import
    [java.io StringWriter]))


(def default-search-result-format :json)

(def base-mime-type-to-format
  "A map of base mime types to the format symbols supported"
  {"*/*" default-search-result-format
   "application/json" :json
   "application/xml" :xml
   "text/csv" :csv})

(defn mime-type->format
  "Converts a mime-type into the format requested."
  [mime-type]
  (if mime-type
    (get base-mime-type-to-format
         (str (mt/base-type (mt/parse mime-type))))
    default-search-result-format))

(def format->mime-type
  {:json "application/json"
   :xml "application/xml"
   :csv "text/csv"})

(defn validate-search-result-mime-type
  "Validates the requested search result mime type."
  [mime-type]
  (when-not (mime-type->format mime-type)
    (errors/throw-service-error
      :bad-request (format "The mime type [%s] is not supported for search results." mime-type))))

(defmulti search-results->response
  (fn [results result-type pretty]
    result-type))

(defmethod search-results->response :json
  [results result-type pretty]
  (let [{:keys [hits took references]} results
        response-refs (map #(set/rename-keys % {:concept-id :id}) references)
        response-results (r/->Results hits took response-refs)]
    (json/generate-string response-results {:pretty pretty})))

(defn- reference->xml-element
  "Converts a search result reference into an XML element"
  [reference]
  (let [{:keys [concept-id revision-id location name]} reference]
    (x/element :reference {}
               (x/element :name {} name)
               (x/element :id {} concept-id)
               (x/element :location {} location)
               (x/element :revision-id {} (str revision-id)))))

(defmethod search-results->response :xml
  [results result-typ pretty]
  (let [{:keys [hits took references]} results
        xml-fn (if pretty x/indent-str x/emit-str)]
    (xml-fn
      (x/element :results {}
                 (x/element :hits {} (str hits))
                 (x/element :took {} (str took))
                 (x/->Element :references {}
                              (map reference->xml-element references))))))

(def CSV_HEADER
  ["Granule UR","Producer Granule ID","Start Time","End Time","Online Access URLs","Browse URLs","Cloud Cover","Day/Night","Size"])

(defmethod search-results->response :csv
  [results result-type pretty]
  (let [{:keys [hits took references]} results
        response-refs (conj references CSV_HEADER)
        string-writer (StringWriter.)]
    (csv/write-csv string-writer response-refs)
    (str string-writer)))

