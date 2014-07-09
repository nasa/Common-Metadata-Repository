(ns cmr.search.results-handlers.atom-results-handler
  "Handles the ATOM results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [clojure.data.xml :as x]
            [clojure.string :as str]
            [clj-time.core :as time]))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :atom]
  [concept-type result-format]
  ["granule-ur"
   "entry-title"
   "producer-gran-id"
   "size"
   "original-format"
   "provider-id"
   "start-date"
   "end-date"
   "downloadable-urls"
   "browse-urls"
   "documentation-urls"
   "metadata-urls"
   "downloadable"
   "browsable"
   "day-night"
   "cloud-cover"])

(defmethod elastic-results/elastic-result->query-result-item :atom
  [context query elastic-result]
  (let [{concept-id :_id
         revision-id :_version
         {[granule-ur] :granule-ur
          [entry-title] :entry-title
          [producer-gran-id] :producer-gran-id
          [size] :size
          [original-format] :original-format
          [provider-id] :provider-id
          [start-date] :start-date
          [end-date] :end-date
          downloadable-urls :downloadable-urls
          browse-urls :browse-urls
          documentation-urls :documentation-urls
          metadata-urls :metadata-urls
          [downloadable] :downloadable
          [browsable] :browsable
          [day-night] :day-night
          [cloud-cover] :cloud-cover} :fields} elastic-result
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))]
    {:id concept-id
     :title granule-ur
     ;; TODO: last-updated is not indexed yet
     ; :updated last-updated
     :dataset-id entry-title
     :producer-gran-id producer-gran-id
     :size (str size)
     :original-format original-format
     :data-center provider-id
     :start-date start-date
     :end-date end-date
     :downloadable-urls downloadable-urls
     :browse-urls browse-urls
     :documentation-urls documentation-urls
     :metadata-urls metadata-urls
     ;; TODO spatial info goes here
     :online-access-flag downloadable
     :browse-flag browsable
     :day-night day-night
     :cloud-cover (str cloud-cover)}))

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

;; TODO make this concept-type->atom-title
(defn- atom-title
  "Returns the title of atom"
  [context]
  (if (re-find #"granules" (:concept-type-w-extension context))
    "ECHO granule metadata"
    "ECHO dataset metadata"))

(def link-type->link-type-uri
  {:download "http://esipfed.org/ns/fedsearch/1.1/data#"
   :browse "http://esipfed.org/ns/fedsearch/1.1/browse#"
   :documentation "http://esipfed.org/ns/fedsearch/1.1/documentation#"
   :metadata "http://esipfed.org/ns/fedsearch/1.1/metadata#"})

(defn- link->xml-element
  "Convert a link to an XML element"
  [type link]
  (x/element :link {:href link :rel (link-type->link-type-uri type)}))

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
               (map (partial link->xml-element :download) downloadable-urls)
               (map (partial link->xml-element :browse) browse-urls)
               (map (partial link->xml-element :documentation) documentation-urls)
               (map (partial link->xml-element :metadata) metadata-urls)
               (x/element :echo:onlineAccessFlag {} online-access-flag)
               (x/element :echo:browseFlag {} browse-flag)
               (when day-night (x/element :echo:dayNightFlag {} day-night))
               (when cloud-cover (x/element :echo:cloudCover {} cloud-cover)))))

(defmethod qs/search-results->response :atom
  [context query results]
  (let [{:keys [hits took items]} results
        xml-fn (if (:pretty? query) x/indent-str x/emit-str)]
    (xml-fn
      (x/element :feed ATOM_HEADER_ATTRIBUTES
                 (x/element :updated {} (str (time/now)))
                 (x/element :id {} (:atom-request-url context))
                 (x/element :title {:type "text"} (atom-title context))
                 (map atom-reference->xml-element items)))))


