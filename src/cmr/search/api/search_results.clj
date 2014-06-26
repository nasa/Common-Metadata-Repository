(ns cmr.search.api.search-results
  "Contains functions for validating search results requested formats and for converting to
  requested format"
  (:require [cheshire.core :as json]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as ct]
            [cmr.common.mime-types :as mt]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [clojure.data.csv :as csv]
            [clojure.string :as s]
            [cmr.search.models.results :as r]
            [cmr.transmit.transformer :as t])
  (:import
    [java.io StringWriter]))


(def default-search-result-format :json)

(def supported-mime-types
  "The mime types supported by search."
  #{"*/*"
    "application/xml"
    "application/json"
    "application/echo10+xml"
    "application/dif+xml"
    "text/csv"})

(defmulti search-results->response
  (fn [context results result-type pretty]
    result-type))

(defmethod search-results->response :json
  [context results result-type pretty]
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

(defn- remove-xml-processing-instructions
  "Removes xml processing instructions from XML so it can be embedded in another XML document"
  [xml]
  (let [processing-regex #"<\?.*?\?>"
        doctype-regex #"<!DOCTYPE.*?>"]
    (-> xml
        (s/replace processing-regex "")
        (s/replace doctype-regex ""))))

(defmulti reference+metadata->xml-element
  "Converts a search result + metadata into an XML element"
  (fn [reference metadata]
    (ct/concept-id->type (:concept-id reference))))

(defmethod reference+metadata->xml-element :granule
  [reference metadata]
  (let [{:keys [concept-id collection-concept-id revision-id]} reference]
    (format "<result concept-id=\"%s\" collection-concept-id=\"%s\" revision-id=\"%s\">%s</result>"
            concept-id
            collection-concept-id
            revision-id
            metadata)))

(defmethod reference+metadata->xml-element :collection
  [reference metadata]
  (let [{:keys [concept-id revision-id]} reference]
    (format "<result concept-id=\"%s\" revision-id=\"%s\">%s</result>"
            concept-id
            revision-id
            metadata)))

(defn- references->format
  "Converts search result references into the desired format"
  [context references format]
  (let [tuples (map #(vector (:concept-id %) (:revision-id %)) references)]
    (t/get-formatted-concept-revisions context tuples format)))

(defmethod search-results->response :xml
  [context results result-type pretty]
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
  [context results result-type pretty]
  (let [{:keys [hits took references]} results
        response-refs (conj references CSV_HEADER)
        string-writer (StringWriter.)]
    (csv/write-csv string-writer response-refs)
    (str string-writer)))

(defn search-results->metadata-response
  [context results result-type pretty]
  (let [{:keys [hits took references]} results
        metadatas (references->format context references result-type)]
    (format "<?xml version=\"1.0\" encoding=\"UTF-8\"?><results><hits>%d</hits><took>%d</took>%s</results>"
            hits took
            (s/join "" (map reference+metadata->xml-element
                            references
                            (map remove-xml-processing-instructions
                                 metadatas))))))

(defmethod search-results->response :echo10
  [context results result-type pretty]
  (search-results->metadata-response context results result-type pretty))

(defmethod search-results->response :dif
  [context results result-type pretty]
  (search-results->metadata-response context results result-type pretty))

