(ns cmr.search.api.atom-search-results
  "Contains functions for converting to atom format"
  (:require [clojure.data.xml :as x]
            [clj-time.core :as time]
            [cmr.search.api.search-results :as sr]))

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

(defmethod sr/search-results->response :atom
  [context results result-type pretty]
  (let [{:keys [hits took references]} results
        xml-fn (if pretty x/indent-str x/emit-str)]
    (xml-fn
      (x/element :feed ATOM_HEADER_ATTRIBUTES
                 (x/element :updated {} (str (time/now)))
                 (x/element :id {} (:atom-request-url context))
                 (x/element :title {:type "text"} (atom-title context))
                 (map atom-reference->xml-element references)))))
