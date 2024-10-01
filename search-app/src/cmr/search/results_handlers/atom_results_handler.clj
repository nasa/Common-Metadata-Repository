(ns cmr.search.results-handlers.atom-results-handler
  "Handles the ATOM results format and related functions"
  (:require
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clojure.data.xml :as xml]
   [clojure.edn :as edn]
   [clojure.walk :as walk]
   [cmr.common-app.services.search :as qs]
   [cmr.common.services.search.results-model :as r]
   [cmr.common.util :as util]
   [cmr.elastic-utils.search.es-acl-parser :as acl-rhh]
   [cmr.elastic-utils.search.es-index :as elastic-search-index]
   [cmr.elastic-utils.search.es-results-to-query-results :as er-to-qr]
   [cmr.search.models.query :as q]
   [cmr.search.results-handlers.atom-links-results-handler :as atom-links]
   [cmr.search.results-handlers.atom-spatial-results-handler :as atom-spatial]
   [cmr.search.results-handlers.orbit-swath-results-helper :as orbit-swath-helper]
   [cmr.search.services.query-execution.facets.facets-results-feature :as frf]
   [cmr.search.services.query-execution.granule-counts-results-feature :as gcrf]
   [cmr.search.services.query-execution.tags-results-feature :as trf]
   [cmr.search.services.url-helper :as url]
   [cmr.spatial.serialize :as srl]
   [cmr.umm-spec.util :as spec-util]))

(def metadata-format->atom-original-format
  "Defines the concept metadata format to atom original-format mapping"
  {"echo10" "ECHO10"
   "iso-smap" "ISO-SMAP"
   "iso19115" "ISO19115"
   "dif" "DIF"
   "dif10" "DIF10"
   "umm-json" "UMM_JSON"})

(defmethod elastic-search-index/concept-type+result-format->fields [:collection :atom]
  [concept-type query]
  (let [atom-fields ["short-name"
                     "version-id"
                     "entry-id"
                     "summary"
                     "update-time"
                     "entry-title"
                     "collection-data-type"
                     "data-center"
                     "archive-center"
                     "processing-level-id"
                     "metadata-format"
                     "provider-id"
                     "start-date"
                     "end-date"
                     "atom-links"
                     "associated-difs"
                     "downloadable"
                     "browsable"
                     "coordinate-system"
                     "swath-width"
                     "period"
                     "inclination-angle"
                     "number-of-orbits"
                     "start-circular-latitude"
                     "ords-info"
                     "ords"
                     "has-variables"
                     "has-formats"
                     "has-transforms"
                     "has-combine"
                     "has-spatial-subsetting"
                     "has-temporal-subsetting"
                     "cloud-hosted"
                     "platforms"
                     "consortiums"
                     "service-features-gzip-b64"
                     "associations-gzip-b64"
                     "_score"
                     "eula-identifiers"]
        atom-fields (if (contains? (set (:result-features query)) :tags)
                      (conj atom-fields trf/stored-tags-field)
                      atom-fields)]
    (distinct (concat atom-fields acl-rhh/collection-elastic-fields))))

(defmethod elastic-search-index/concept-type+result-format->fields [:granule :atom]
  [concept-type query]
  (let [atom-fields ["granule-ur"
                     "collection-concept-id"
                     "update-time"
                     "entry-title"
                     "producer-gran-id"
                     "size"
                     "metadata-format"
                     "provider-id"
                     "start-date"
                     "end-date"
                     "atom-links"
                     "orbit-asc-crossing-lon"
                     "start-lat"
                     "start-direction"
                     "end-lat"
                     "end-direction"
                     "orbit-calculated-spatial-domains-json"
                     "downloadable"
                     "browsable"
                     "day-night"
                     "cloud-cover"
                     "coordinate-system"
                     "ords-info"
                     "ords"]]
    (vec (distinct
           (concat atom-fields
                   orbit-swath-helper/orbit-elastic-fields
                   acl-rhh/granule-elastic-fields)))))

(def base-has-features
  "default has_* features in JSON format"
  {:has-formats false
   :has-variables false
   :has-transforms false
   :has-combine false
   :has-spatial-subsetting false
   :has-temporal-subsetting false})

(defn- gzip-b64-str->service-features
  "Returns the service features in JSON format from the gzipped base64 string"
  [service-features-gzip-b64]
  (let [service-features (if service-features-gzip-b64
                           (-> service-features-gzip-b64
                               util/gzip-base64->string
                               edn/read-string)
                           {})]
    (-> service-features
        (update :opendap #(merge base-has-features %))
        (update :esi #(merge base-has-features %))
        (update :harmony #(merge base-has-features %))
        util/map-keys->snake_case)))

(defn- collection-elastic-result->query-result-item
  [elastic-result]
  (let [{concept-id :_id
         score :_score
         {short-name :short-name
          version-id :version-id
          entry-id :entry-id
          summary :summary
          update-time :update-time
          entry-title :entry-title
          collection-data-type :collection-data-type
          processing-level-id :processing-level-id
          metadata-format :metadata-format
          provider-id :provider-id
          archive-center :archive-center
          organizations :data-center
          start-date :start-date
          end-date :end-date
          atom-links :atom-links
          associated-difs :associated-difs
          downloadable :downloadable
          browsable :browsable
          coordinate-system :coordinate-system
          ords-info :ords-info
          ords :ords
          access-value :access-value
          swath-width :swath-width
          period :period
          inclination-angle :inclination-angle
          number-of-orbits :number-of-orbits
          start-circular-latitude :start-circular-latitude
          has-variables :has-variables
          has-formats :has-formats
          has-transforms :has-transforms
          has-combine :has-combine
          has-spatial-subsetting :has-spatial-subsetting
          has-temporal-subsetting :has-temporal-subsetting
          cloud-hosted :cloud-hosted
          platforms :platforms
          consortiums :consortium
          eula-identifiers :eula-identifiers
          service-features-gzip-b64 :service-features-gzip-b64
          associations-gzip-b64 :associations-gzip-b64} :_source} elastic-result
        start-date (acl-rhh/parse-elastic-datetime start-date)
        end-date (acl-rhh/parse-elastic-datetime end-date)
        atom-links (map #(json/decode % true) atom-links)
        ;; DIF collection has a special case on associated-difs where it is set to its entry-id
        ;; For DIF collection, its entry-id is the same as its short-name
        associated-difs (case metadata-format
                          "dif" [(spec-util/entry-id short-name version-id)]
                          "dif10" [(spec-util/entry-id short-name version-id)]
                          associated-difs)
        archive-center (if (sequential? archive-center)
                         (first archive-center)
                         archive-center)
        collection-data-type (if (sequential? collection-data-type)
                               (first collection-data-type)
                               collection-data-type)]
    (merge {:id concept-id
            :score (q/normalize-score score)
            :title entry-title
            :short-name short-name
            :version-id version-id
            :entry-id entry-id
            :summary summary
            :updated update-time
            :dataset-id entry-title
            :collection-data-type collection-data-type
            :processing-level-id processing-level-id
            :original-format (metadata-format->atom-original-format metadata-format)
            :data-center provider-id
            :archive-center archive-center
            :organizations (when (seq organizations) organizations)
            :platforms (map :short-name platforms)
            :consortiums consortiums
            :start-date start-date
            :end-date end-date
            :atom-links atom-links
            :online-access-flag downloadable
            :browse-flag browsable
            :associated-difs associated-difs
            :coordinate-system coordinate-system
            :shapes (srl/ords-info->shapes ords-info ords)
            :orbit-parameters {:swath-width swath-width
                               :period period
                               :inclination-angle inclination-angle
                               :number-of-orbits number-of-orbits
                               :start-circular-latitude start-circular-latitude}
            :tags (trf/collection-elastic-result->tags elastic-result)
            :has-variables has-variables
            :has-formats has-formats
            :has-transforms has-transforms
            :has-combine has-combine
            :has-spatial-subsetting has-spatial-subsetting
            :has-temporal-subsetting has-temporal-subsetting
            :cloud-hosted cloud-hosted
            :service-features (gzip-b64-str->service-features service-features-gzip-b64)
            :associations (some-> associations-gzip-b64
                                  util/gzip-base64->string
                                  edn/read-string)
            :eula-identifiers eula-identifiers}
           (acl-rhh/parse-elastic-item :collection elastic-result))))

(defn- granule-elastic-result->query-result-item
  [orbits-by-collection elastic-result]
  (let [{concept-id :_id
         {granule-ur :granule-ur
          collection-concept-id :collection-concept-id
          update-time :update-time
          entry-title :entry-title
          producer-gran-id :producer-gran-id
          size :size
          metadata-format :metadata-format
          provider-id :provider-id
          start-date :start-date
          end-date :end-date
          atom-links :atom-links
          ascending-crossing :orbit-asc-crossing-lon
          start-lat :start-lat
          start-direction :start-direction
          end-lat :end-lat
          end-direction :end-direction
          orbit-calculated-spatial-domains-json :orbit-calculated-spatial-domains-json
          downloadable :downloadable
          browsable :browsable
          day-night :day-night
          cloud-cover :cloud-cover
          coordinate-system :coordinate-system
          ords-info :ords-info
          ords :ords
          access-value :access-value} :_source} elastic-result
        atom-links (map (fn [link-str]
                          (update-in (json/decode link-str true) [:size] #(when % (str %))))
                        atom-links)
        orbit (when ascending-crossing
                {:ascending-crossing (util/double->string ascending-crossing)
                 :start-lat (util/double->string start-lat)
                 :start-direction start-direction
                 :end-lat (util/double->string end-lat)
                 :end-direction end-direction})
        orbit-calculated-spatial-domains (map orbit-swath-helper/ocsd-json->map
                                              orbit-calculated-spatial-domains-json)
        shapes (concat (srl/ords-info->shapes ords-info ords)
                       (when (and start-date end-date)
                         (orbit-swath-helper/elastic-result->swath-shapes
                           orbits-by-collection elastic-result)))]
    (merge {:id concept-id
            :title granule-ur
            :collection-concept-id collection-concept-id
            :updated update-time
            :dataset-id entry-title
            :producer-gran-id producer-gran-id
            :size (when size (str size))
            :original-format (metadata-format->atom-original-format metadata-format)
            :data-center provider-id
            :start-date (acl-rhh/parse-elastic-datetime start-date)
            :end-date (acl-rhh/parse-elastic-datetime end-date)
            :atom-links atom-links
            :orbit orbit
            :orbit-calculated-spatial-domains orbit-calculated-spatial-domains
            :online-access-flag downloadable
            :browse-flag browsable
            :day-night day-night
            :cloud-cover (when cloud-cover (str cloud-cover))
            :coordinate-system coordinate-system
            :shapes shapes}
           (acl-rhh/parse-elastic-item :granule elastic-result))))

(defn- granule-elastic-results->query-result-items
  [context query elastic-matches]
  (let [orbits-by-collection (orbit-swath-helper/get-orbits-by-collection context elastic-matches)]
    (pmap (partial granule-elastic-result->query-result-item orbits-by-collection) elastic-matches)))

(defn- elastic-results->query-results
  [context query elastic-results]
  (let [hits (er-to-qr/get-hits elastic-results)
        timed-out (er-to-qr/get-timedout elastic-results)
        scroll-id (er-to-qr/get-scroll-id elastic-results)
        search-after (er-to-qr/get-search-after elastic-results)
        elastic-matches (er-to-qr/get-elastic-matches elastic-results)
        items (if (= :granule (:concept-type query))
                (granule-elastic-results->query-result-items context query elastic-matches)
                (map collection-elastic-result->query-result-item elastic-matches))]
    (r/map->Results {:hits hits
                     :items items
                     :timed-out timed-out
                     :result-format (:result-format query)
                     :scroll-id scroll-id
                     :search-after search-after})))

(defmethod er-to-qr/elastic-results->query-results [:collection :atom]
  [context query elastic-results]
  (elastic-results->query-results context query elastic-results))

(defmethod er-to-qr/elastic-results->query-results [:granule :atom]
  [context query elastic-results]
  (elastic-results->query-results context query elastic-results))

(defmethod gcrf/query-results->concept-ids :atom
  [results]
  (->> results
       :items
       (map :id)))

(def ATOM_HEADER_ATTRIBUTES
  "The set of attributes that go on the ATOM root element"
  {:xmlns "http://www.w3.org/2005/Atom"
   :xmlns:dc "http://purl.org/dc/elements/1.1/"
   :xmlns:georss "http://www.georss.org/georss/10"
   :xmlns:time "http://a9.com/-/opensearch/extensions/time/1.0/"
   :xmlns:echo "https://cmr.earthdata.nasa.gov/search/site/docs/search/api.html#atom"
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
   :metadata "http://esipfed.org/ns/fedsearch/1.1/metadata#"
   :service "http://esipfed.org/ns/fedsearch/1.1/service#"
   :s3 "http://esipfed.org/ns/fedsearch/1.1/s3#"
   :search "http://esipfed.org/ns/fedsearch/1.1/search#"})

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
                    (add-attribs :length size)
                    (add-attribs :rel (link-type->link-type-uri (keyword link-type)))
                    (add-attribs :type mime-type)
                    (add-attribs :title title)
                    (add-attribs :hreflang "en-US")
                    (add-attribs :href href))]
    attribs))

(defn- atom-link->xml-element
  "Convert an atom link to an XML element"
  [atom-link]
  (xml/element :link (atom-link->attribute-map atom-link)
             (when (:inherited atom-link)
               (xml/element :echo:inherited))))

(defn- organization->xml-element
  "Convert an organization to an XML element"
  [org]
  (xml/element :echo:organization {} org))

(defn- tag->xml-element
  "Convert a tag to an XML element"
  [tag]
  (let [[tag-key {:keys [data]}] tag
        data-str (when data
                   (json/generate-string data))]
    (xml/element :echo:tag {}
               (xml/element :echo:tagKey {} tag-key)
               (when data-str
                 (xml/element :echo:data {} data-str)))))

(defn- orbit-parameters->attribute-map
  "Convert orbit parameters into attributes for an XML element"
  [orbit-params]
  (let [{:keys [swath-width period inclination-angle number-of-orbits start-circular-latitude]}
        orbit-params]
    (-> {}
        (add-attribs :swathWidth swath-width)
        (add-attribs :period period)
        (add-attribs :inclinationAngle inclination-angle)
        (add-attribs :numberOfOrbits number-of-orbits)
        (add-attribs :startCircularLatitude start-circular-latitude))))

(defn- orbit-parameters->xml-element
  "Convert orbit parameters into an XML element"
  [orbit-params]
  (xml/element :echo:orbitParameters (orbit-parameters->attribute-map orbit-params)))

(defn- ocsd->attribute-map
  "Convert an oribt calculated spatial domain to attributes for an XML element"
  [ocsd]
  (let [ocsd (walk/keywordize-keys ocsd)
        {:keys [orbital-model-name
                orbit-number
                start-orbit-number
                stop-orbit-number
                equator-crossing-longitude
                equator-crossing-date-time]} ocsd]
    (-> {}
        (add-attribs :orbitModelName orbital-model-name)
        (add-attribs :orbitNumber orbit-number)
        (add-attribs :startOrbitNumber start-orbit-number)
        (add-attribs :stopOrbitNumber stop-orbit-number)
        (add-attribs :equatorCrossingLongitude equator-crossing-longitude)
        (add-attribs :equatorCrossingDateTime equator-crossing-date-time))))

(defn- ocsd->xml-element
  "Convert an oribt calculated spatial domain to an XML element"
  [ocsd]
  (xml/element :echo:orbitCalSpatialDomain (ocsd->attribute-map ocsd)))

(defn- orbit->xml-element
  "Convert an oribt to an XML element"
  [orbit]
  (let [{:keys [ascending-crossing start-lat start-direction end-lat end-direction]} orbit
        orbit-attrib-map (-> {}
                             (add-attribs :ascendingCrossing ascending-crossing)
                             (add-attribs :startLatitude start-lat)
                             (add-attribs :startDirection start-direction)
                             (add-attribs :endLatitude end-lat)
                             (add-attribs :endDirection end-direction))]
    (xml/element :echo:orbit orbit-attrib-map)))

(defmulti atom-reference->xml-element
  (fn [results concept-type reference]
    concept-type))

(defmethod atom-reference->xml-element :collection
  [results concept-type reference]
  (let [{:keys [has-granules-map granule-counts-map]} results
        {:keys [id score title short-name version-id summary updated dataset-id collection-data-type
                processing-level-id original-format data-center archive-center start-date end-date
                atom-links associated-difs online-access-flag browse-flag coordinate-system shapes
                orbit-parameters organizations tags has-variables has-formats has-transforms has-combine
                has-spatial-subsetting has-temporal-subsetting cloud-hosted consortiums]} reference
        granule-count (get granule-counts-map id 0)]
    (xml/element :entry {}
               (xml/element :id {} id)
               (map #(xml/element :consortium {} %) consortiums)
               (xml/element :title {:type "text"} title)
               (xml/element :summary {:type "text"} summary)
               (xml/element :updated {} updated)
               (xml/element :echo:datasetId {} dataset-id)
               (xml/element :echo:shortName {} short-name)
               (xml/element :echo:versionId {} version-id)
               (xml/element :echo:originalFormat {} original-format)
               (when collection-data-type (xml/element :echo:collectionDataType {} collection-data-type))
               (xml/element :echo:dataCenter {} data-center)
               (when archive-center (xml/element :echo:archiveCenter {} archive-center))
               (map organization->xml-element organizations)
               (when processing-level-id (xml/element :echo:processingLevelId {} processing-level-id))
               (when start-date (xml/element :time:start {} (str start-date)))
               (when end-date (xml/element :time:end {} (str end-date)))
               (map atom-link->xml-element atom-links)
               (when coordinate-system (xml/element :echo:coordinateSystem {} coordinate-system))
               (when orbit-parameters (orbit-parameters->xml-element orbit-parameters))
               (map atom-spatial/shape->xml-element shapes)
               (map #(xml/element :echo:difId {} %) associated-difs)
               (xml/element :echo:onlineAccessFlag {} online-access-flag)
               (xml/element :echo:browseFlag {} browse-flag)
               (when has-granules-map
                 (xml/element :echo:hasGranules {} (or (< 0 granule-count)
                                                     (get has-granules-map id false))))
               (when granule-counts-map
                 (xml/element :echo:granuleCount {} granule-count))
               (xml/element :echo:hasVariables {} has-variables)
               (xml/element :echo:hasFormats {} has-formats)
               (xml/element :echo:hasTransforms {} has-transforms)
               (xml/element :echo:hasCombine {} has-combine)
               (xml/element :echo:hasSpatialSubsetting {} has-spatial-subsetting)
               (xml/element :echo:hasTemporalSubsetting {} has-temporal-subsetting)
               (xml/element :echo:cloudHosted {} cloud-hosted)
               (when score (xml/element :relevance:score {} score))
               (map tag->xml-element tags))))

(defmethod atom-reference->xml-element :granule
  [results concept-type reference]
  (let [{:keys [id score title updated dataset-id producer-gran-id size original-format
                data-center start-date end-date atom-links online-access-flag browse-flag
                collection-concept-id day-night cloud-cover coordinate-system shapes
                orbit orbit-calculated-spatial-domains]} reference]
    (xml/element :entry {}
               (xml/element :id {} id)
               (xml/element :title {:type "text"} title)
               (xml/element :updated {} updated)
               (xml/element :echo:datasetId {} dataset-id)
               (xml/element :echo:collectionConceptId {} collection-concept-id)
               (when producer-gran-id (xml/element :echo:producerGranuleId {} producer-gran-id))
               (when size (xml/element :echo:granuleSizeMB {} size))
               (xml/element :echo:originalFormat {} original-format)
               (xml/element :echo:dataCenter {} data-center)
               (when start-date (xml/element :time:start {} (str start-date)))
               (when end-date (xml/element :time:end {} (str end-date)))
               (map atom-link->xml-element atom-links)
               (when orbit (orbit->xml-element orbit))
               (map ocsd->xml-element orbit-calculated-spatial-domains)
               (when coordinate-system (xml/element :echo:coordinateSystem {} coordinate-system))
               (map atom-spatial/shape->xml-element shapes)
               (xml/element :echo:onlineAccessFlag {} online-access-flag)
               (xml/element :echo:browseFlag {} browse-flag)
               (when day-night (xml/element :echo:dayNightFlag {} day-night))
               (when cloud-cover (xml/element :echo:cloudCover {} cloud-cover)))))

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
                        (map #(assoc % :inherited true))
                        (concat atom-links))]
    (assoc reference :atom-links atom-links)))

(defn append-collection-links
  "Returns the references after appending collection non-downloadable links into the atom-links"
  [context refs]
  (let [collection-concept-ids (distinct (map :collection-concept-id refs))
        collection-links-map (atom-links/find-collection-atom-links context collection-concept-ids)]
    (map (partial append-links collection-links-map) refs)))

(defn- search-results->response
  [context query results]
  (let [{:keys [hits took items facets]} results
        {:keys [concept-type result-format]} query
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
    (xml/emit-str
      (xml/element :feed header-attributes
                 (xml/element :updated {} (str (time/now)))
                 (xml/element :id {} (url/atom-request-url context concept-type result-format))
                 (xml/element :title {:type "text"} (concept-type->atom-title concept-type))
                 (map (partial atom-reference->xml-element results concept-type) items)
                 (frf/facets->xml-element "echo" facets)))))

(defn- single-result->response
  [context query results]
  {:pre [(<= (count (:items results)) 1)]}
  (qs/search-results->response context query results))

(doseq [concept-type [:collection :granule]]
  (defmethod qs/search-results->response [concept-type :atom]
    [context query results]
    (search-results->response context query results))

  (defmethod qs/single-result->response [concept-type :atom]
    [context query results]
    (single-result->response context query results)))
