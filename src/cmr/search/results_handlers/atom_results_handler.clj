(ns cmr.search.results-handlers.atom-results-handler
  "Handles the ATOM results format and related functions"
  (:require [cmr.search.data.elastic-results-to-query-results :as elastic-results]
            [cmr.search.data.elastic-search-index :as elastic-search-index]
            [cmr.search.services.query-service :as qs]
            [cmr.search.services.query-walkers.granule-count-query-extractor :as gcqe]
            [clojure.data.xml :as x]
            [cheshire.core :as json]
            [clojure.string :as str]
            [clj-time.core :as time]
            [cmr.spatial.serialize :as srl]
            [cmr.search.services.url-helper :as url]
            [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]
            [cmr.search.results-handlers.atom-links-results-handler :as atom-links]))

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :atom]
  [concept-type result-format]
  ["short-name"
   "version-id"
   "summary"
   "update-time"
   "entry-title"
   "collection-data-type"
   "data-center"
   "archive-center"
   "processing-level-id"
   "original-format"
   "provider-id"
   "start-date"
   "end-date"
   "atom-links"
   "associated-difs"
   "downloadable"
   "browsable"
   "coordinate-system"
   "ords-info"
   "ords"
   "_score"])

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :atom]
  [concept-type result-format]
  ["granule-ur"
   "collection-concept-id"
   "update-time"
   "entry-title"
   "producer-gran-id"
   "size"
   "original-format"
   "provider-id"
   "start-date"
   "end-date"
   "atom-links"
   "downloadable"
   "browsable"
   "day-night"
   "cloud-cover"
   "coordinate-system"
   "ords-info"
   "ords"])

(defn- collection-elastic-result->query-result-item
  [elastic-result]
  (let [{concept-id :_id
         score :_score
         {[short-name] :short-name
          [version-id] :version-id
          [summary] :summary
          [update-time] :update-time
          [entry-title] :entry-title
          [collection-data-type] :collection-data-type
          [processing-level-id] :processing-level-id
          [original-format] :original-format
          [provider-id] :provider-id
          [archive-center] :archive-center
          [start-date] :start-date
          [end-date] :end-date
          atom-links :atom-links
          associated-difs :associated-difs
          [downloadable] :downloadable
          [browsable] :browsable
          [coordinate-system] :coordinate-system
          ords-info :ords-info
          ords :ords} :fields} elastic-result
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))
        atom-links (map #(json/decode % true) atom-links)]
    {:id concept-id
     :score score
     :title entry-title
     :short-name short-name
     :version-id version-id
     :summary summary
     :updated update-time
     :dataset-id entry-title
     :collection-data-type collection-data-type
     :processing-level-id processing-level-id
     :original-format original-format
     :data-center provider-id
     :archive-center archive-center
     :start-date start-date
     :end-date end-date
     :atom-links atom-links
     :online-access-flag downloadable
     :browse-flag browsable
     :associated-difs associated-difs
     :coordinate-system coordinate-system
     :shapes (srl/ords-info->shapes ords-info ords)}))

(defn- granule-elastic-result->query-result-item
  [elastic-result]
  (let [{concept-id :_id
         {[granule-ur] :granule-ur
          [collection-concept-id] :collection-concept-id
          [update-time] :update-time
          [entry-title] :entry-title
          [producer-gran-id] :producer-gran-id
          [size] :size
          [original-format] :original-format
          [provider-id] :provider-id
          [start-date] :start-date
          [end-date] :end-date
          atom-links :atom-links
          [downloadable] :downloadable
          [browsable] :browsable
          [day-night] :day-night
          [cloud-cover] :cloud-cover
          [coordinate-system] :coordinate-system
          ords-info :ords-info
          ords :ords} :fields} elastic-result
        start-date (when start-date (str/replace (str start-date) #"\+0000" "Z"))
        end-date (when end-date (str/replace (str end-date) #"\+0000" "Z"))
        atom-links (map (fn [link-str]
                          (update-in (json/decode link-str true) [:size] #(when % (str %))))
                        atom-links)]
    {:id concept-id
     :title granule-ur
     :collection-concept-id collection-concept-id
     :updated update-time
     :dataset-id entry-title
     :producer-gran-id producer-gran-id
     :size (str size)
     :original-format original-format
     :data-center provider-id
     :start-date start-date
     :end-date end-date
     :atom-links atom-links
     :online-access-flag downloadable
     :browse-flag browsable
     :day-night day-night
     :cloud-cover (str cloud-cover)
     :coordinate-system coordinate-system
     :shapes (srl/ords-info->shapes ords-info ords)}))

(defmethod elastic-results/elastic-result->query-result-item :atom
  [context query elastic-result]
  (if (= :granule (:concept-type query))
    (granule-elastic-result->query-result-item elastic-result)
    (collection-elastic-result->query-result-item elastic-result)))

(defmethod gcqe/query-results->concept-ids :atom
  [results]
  (->> results
       :items
       (map :id)))

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

(defn concept-type->atom-title
  "Returns the title of atom"
  [concept-type]
  (if (= concept-type :granule)
    "ECHO granule metadata"
    "ECHO dataset metadata"))

(def link-type->link-type-uri
  {:data "http://esipfed.org/ns/fedsearch/1.1/data#"
   :browse "http://esipfed.org/ns/fedsearch/1.1/browse#"
   :documentation "http://esipfed.org/ns/fedsearch/1.1/documentation#"
   :metadata "http://esipfed.org/ns/fedsearch/1.1/metadata#"})

(defn- add-attribs
  "Returns the attribs with the field-value pair added if there is a value"
  [attribs field value]
  (if (or (nil? value)
          (and (string? value)
               (empty? value)))
    attribs
    (assoc attribs field value)))



(defn atom-link->attribute-map
  "Convert an atom link to an XML element"
  [atom-link]
  (let [{:keys [href link-type title mime-type size inherited]} atom-link
        attribs (-> {}
                    (add-attribs :inherited inherited)
                    (add-attribs :size size)
                    (add-attribs :rel (link-type->link-type-uri (keyword link-type)))
                    (add-attribs :type mime-type)
                    (add-attribs :title title)
                    (add-attribs :hreflang "en-US")
                    (add-attribs :href href))]
    attribs))

(defn- atom-link->xml-element
  "Convert an atom link to an XML element"
  [atom-link]
  (x/element :link (atom-link->attribute-map atom-link)))

(defn- collection-atom-reference->xml-element
  "Converts a collection search result atom reference into an XML element"
  [reference]
  (let [{:keys [id score title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center start-date end-date
                atom-links associated-difs online-access-flag browse-flag coordinate-system shapes]} reference]
    (x/element :entry {}
               (x/element :id {} id)
               (x/element :title {:type "text"} title)
               (x/element :summary {:type "text"} summary)
               (x/element :updated {} updated)
               (x/element :echo:datasetId {} dataset-id)
               (x/element :echo:shortName {} short-name)
               (x/element :echo:versionId {} version-id)
               (x/element :echo:originalFormat {} original-format)
               (when collection-data-type (x/element :echo:collectionDataType {} collection-data-type))
               (x/element :echo:dataCenter {} data-center)
               (when archive-center (x/element :echo:archiveCenter {} archive-center))
               (when processing-level-id (x/element :echo:processingLevelId {} processing-level-id))
               (when start-date (x/element :time:start {} start-date))
               (when end-date (x/element :time:end {} end-date))
               (map atom-link->xml-element atom-links)
               (when coordinate-system (x/element :echo:coordinateSystem {} coordinate-system))
               (map atom-spatial/shape->xml-element shapes)
               (map #(x/element :echo:difId {} %) associated-difs)
               (x/element :echo:onlineAccessFlag {} online-access-flag)
               (x/element :echo:browseFlag {} browse-flag)
               (when score (x/element :relevance:score {} score)))))

(defn- granule-atom-reference->xml-element
  "Converts a granule search result atom reference into an XML element"
  [reference]
  (let [{:keys [id score title updated dataset-id producer-gran-id size original-format
                data-center start-date end-date atom-links online-access-flag browse-flag
                day-night cloud-cover coordinate-system shapes]} reference]
    (x/element :entry {}
               (x/element :id {} id)
               (x/element :title {:type "text"} title)
               (x/element :updated {} updated)
               (x/element :echo:datasetId {} dataset-id)
               (when producer-gran-id (x/element :echo:producerGranuleId {} producer-gran-id))
               (when size (x/element :echo:granuleSizeMB {} size))
               (x/element :echo:originalFormat {} original-format)
               (x/element :echo:dataCenter {} data-center)
               (when start-date (x/element :time:start {} start-date))
               (when end-date (x/element :time:end {} end-date))
               (map atom-link->xml-element atom-links)
               (when coordinate-system (x/element :echo:coordinateSystem {} coordinate-system))
               (map atom-spatial/shape->xml-element shapes)
               (x/element :echo:onlineAccessFlag {} online-access-flag)
               (x/element :echo:browseFlag {} browse-flag)
               (when day-night (x/element :echo:dayNightFlag {} day-night))
               (when cloud-cover (x/element :echo:cloudCover {} cloud-cover)))))

(defn- append-links
  "Append collection links to the given reference and returns the reference"
  [collection-links-map reference]
  (let [{:keys [collection-concept-id atom-links]} reference
        atom-links (->> (get collection-links-map collection-concept-id)
                        ;; only non-browse links are inherited from the collection
                        (filter #(not= "browse" (:link-type %)))
                        ;; remove duplicate links from the collection links if it already exists in the granule
                        (remove (set atom-links))
                        ;; set the inherited flag for collection links
                        (map #(assoc % :inherited "true"))
                        (concat atom-links))]
    (assoc reference :atom-links atom-links)))

(defn append-collection-links
  "Returns the references after appending collection non-downloadable links into the atom-links"
  [context refs]
  (let [collection-concept-ids (distinct (map :collection-concept-id refs))
        collection-links-map (atom-links/find-collection-atom-links context collection-concept-ids)]
    (map (partial append-links collection-links-map) refs)))

(defmethod qs/search-results->response :atom
  [context query results]
  (let [{:keys [hits took items]} results
        {:keys [concept-type result-format]} query
        xml-fn (if (:pretty? query) x/indent-str x/emit-str)
        atom-reference-to-xml-element-fn (if (= :granule concept-type) granule-atom-reference->xml-element
                                           collection-atom-reference->xml-element)

        items (if (= :granule concept-type)
                (append-collection-links context items)
                items)
        ;; add relence url to header attributes if our entries have scores
        header-attributes ATOM_HEADER_ATTRIBUTES
        header-attributes (if (:score (first items))
                            (merge header-attributes
                                   {:xmlns:relevance
                                    "http://a9.com/-/opensearch/extensions/relevance/1.0/"})
                            header-attributes)]
    (xml-fn
      (x/element :feed header-attributes
                 (x/element :updated {} (str (time/now)))
                 (x/element :id {} (url/atom-request-url context concept-type result-format))
                 (x/element :title {:type "text"} (concept-type->atom-title concept-type))
                 (map atom-reference-to-xml-element-fn items)))))


