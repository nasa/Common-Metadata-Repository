(ns cmr.search.api.search-results
  "Contains functions for validating search results requested formats and for converting to
  requested format"
  (:require [cheshire.core :as json]
            [clojure.data.xml :as x]
            [clojure.set :as set]
            [clojure.data.csv :as csv]
            [clojure.string :as s]
            [clj-time.core :as time]
            [cmr.common.xml :as cx]
            [cmr.common.services.errors :as errors]
            [cmr.common.concepts :as ct]
            [cmr.common.mime-types :as mt]
            [cmr.search.models.results :as r]
            [cmr.transmit.transformer :as t]
            [cmr.umm.dif.collection :as dif-c])
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
    "application/atom+xml"
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

(defmulti reference+metadata->result-string
  "Converts a search result + metadata into a string containing a single result for the metadata format."
  (fn [reference metadata]
    (ct/concept-id->type (:concept-id reference))))

(defmethod reference+metadata->result-string :granule
  [reference metadata]
  (let [{:keys [concept-id collection-concept-id revision-id]} reference]
    (format "<result concept-id=\"%s\" collection-concept-id=\"%s\" revision-id=\"%s\">%s</result>"
            concept-id
            collection-concept-id
            revision-id
            metadata)))

(defmethod reference+metadata->result-string :collection
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

(def ATOM_HEADER_ATTRIBUTES
  "The set of attributes that go on the ATOM root element"
  {:xmlns "http://www.w3.org/2005/Atom"
   :xmlns:dc "http://purl.org/dc/terms/"
   :xmlns:georss "http://www.georss.org/georss/10"
   :xmlns:time "http://a9.com/-/opensearch/extensions/time/1.0/"
   :xmlns:echo "http://www.echo.nasa.gov/esip"
   :xmlns:gml "http://www.opengis.net/gml"
   :xmlns:esipdiscovery "http://commons.esipfed.org/ns/discovery/1.2/"
   :xmlns:os "http://a9.com/-/spec/opensearch/1.1/"
   :esipdiscovery:version "1.2"})

(defn- atom-title
  "Returns the title of atom"
  [context]
  (if (re-find #"granules" (:concept-type-w-extension context))
    "ECHO granule metadata"
    "ECHO dataset metadata"))

(def LINK_TYPE_RELATIONS
  {:download "http://esipfed.org/ns/fedsearch/1.1/data#"
   :browse "http://esipfed.org/ns/fedsearch/1.1/browse#"
   :documentation "http://esipfed.org/ns/fedsearch/1.1/documentation#"
   :metadata "http://esipfed.org/ns/fedsearch/1.1/metadata#"})

(defn- link->xml-element
  "Convert a link to an XML element"
  [type link]
  (x/element :link {:href link :rel (get LINK_TYPE_RELATIONS type)}))

(defn- atom-reference->xml-element
  "Converts a search result atom reference into an XML element"
  [reference]
  (let [{:keys [id title updated dataset-id producer-gran-id
     size original-format data-center start-date end-date
     downloadable-urls browse-urls documentation-urls metadata-urls
     online-access-flag browse-flag day-night cloud-cover]} reference]
    (x/element :entry {}
               (x/element :id {} id)
               (x/element :title {:type "text"} title)
               (x/element :echo:datasetId {} dataset-id)
               (when producer-gran-id (x/element :echo:producerGranuleId {} producer-gran-id))
               (when size (x/element :echo:granuleSizeMB {} size))
               (x/element :echo:originalFormat {} original-format)
               (x/element :echo:dataCenter {} data-center)
               (when start-date (x/element :time:start {} start-date))
               (when end-date (x/element :time:end {} end-date))
               ;; TODO: we need to come up with a way to index the atom links correctly in elasticsearch
               ;; There are multiple fields in the links. Also we need to include links from the collection.
               (map #(link->xml-element :download %) downloadable-urls)
               (map #(link->xml-element :browse %) browse-urls)
               (map #(link->xml-element :documentation %) documentation-urls)
               (map #(link->xml-element :metadata %) metadata-urls)
               (x/element :echo:onlineAccessFlag {} online-access-flag)
               (x/element :echo:browseFlag {} browse-flag)
               (when day-night (x/element :echo:dayNightFlag {} day-night))
               (when cloud-cover (x/element :echo:cloudCover {} cloud-cover)))))

(defmethod search-results->response :atom
  [context results result-type pretty]
  (let [{:keys [hits took references]} results
        xml-fn (if pretty x/indent-str x/emit-str)]
    (xml-fn
      (x/element :feed ATOM_HEADER_ATTRIBUTES
                 (x/element :updated {} (str (time/now)))
                 (x/element :id {} (:atom-request-url context))
                 (x/element :title {:type "text"} (atom-title context))
                 (map atom-reference->xml-element references)))))

(defn search-results->metadata-response
  [context results result-type pretty]
  (let [{:keys [hits took references]} results
        metadatas (references->format context references result-type)
        result-strings (map reference+metadata->result-string
                            references
                            (map remove-xml-processing-instructions
                                 metadatas))
        response (format "<?xml version=\"1.0\" encoding=\"UTF-8\"?><results><hits>%d</hits><took>%d</took>%s</results>"
                         hits took
                         (s/join "" result-strings))]
    (if pretty
      (let [parsed (x/parse-str response)
            ;; Fix for DIF emitting XML
            parsed (if (= :dif result-type)
                     (cx/update-elements-at-path
                       parsed [:result :DIF]
                       assoc :attrs dif-c/dif-header-attributes)
                     parsed)]
        (x/indent-str parsed))

      response)))

(defmethod search-results->response :echo10
  [context results result-type pretty]
  (search-results->metadata-response context results result-type pretty))

(defmethod search-results->response :dif
  [context results result-type pretty]
  (search-results->metadata-response context results result-type pretty))

