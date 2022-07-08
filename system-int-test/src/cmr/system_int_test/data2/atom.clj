(ns cmr.system-int-test.data2.atom
  "Contains helper functions for converting granules into the expected map of parsed atom results."
  (:require
   [camel-snake-kebab.core :as csk]
   [cheshire.core :as json]
   [clj-time.format :as f]
   [clojure.data.xml :as x]
   [clojure.string :as str]
   [cmr.common.concepts :as cu]
   [cmr.common.util :as util]
   [cmr.common.xml :as cx]
   [cmr.search.results-handlers.atom-results-handler :as atom-results-handler]
   [cmr.spatial.line-string :as l]
   [cmr.spatial.mbr :as m]
   [cmr.spatial.point :as p]
   [cmr.spatial.polygon :as poly]
   [cmr.umm-spec.date-util :as date-util]
   [cmr.system-int-test.data2.core :as data-core]
   [cmr.system-int-test.data2.facets :as facets]
   [cmr.system-int-test.data2.granule :as dg]
   [cmr.system-int-test.utils.fast-xml :as fx]
   [cmr.system-int-test.utils.url-helper :as url]
   [cmr.umm.collection.entry-id :as eid]
   [cmr.umm.echo10.spatial :as echo-s]
   [cmr.umm.related-url-helper :as ru]
   [cmr.umm.start-end-date :as sed]
   [cmr.umm.umm-spatial :as umm-s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing the ATOM results

(defn xml-elem->polygons-without-holes
  [entry-elem]
  (map #(poly/polygon [(umm-s/lat-lon-point-str->ring %)])
       (cx/strings-at-path entry-elem [:polygon])))

(defn xml-elem->polygons-with-holes
  [entry-elem]
  (map (fn [elem]
         (let [boundary (umm-s/lat-lon-point-str->ring (cx/string-at-path elem [:exterior :LinearRing :posList]))
               holes (map umm-s/lat-lon-point-str->ring
                          (cx/strings-at-path elem [:interior :LinearRing :posList]))]
           (poly/polygon (cons boundary holes))))
       (cx/elements-at-path entry-elem [:where :Polygon])))

(defn xml-elem->points
  [entry-elem]
  (map (comp first umm-s/lat-lon-point-str->points) (cx/strings-at-path entry-elem [:point])))

(defn xml-elem->lines
  [entry-elem]
  (map (comp l/line-string umm-s/lat-lon-point-str->points) (cx/strings-at-path entry-elem [:line])))

(defn xml-elem->bounding-rectangles
  [entry-elem]
  (map (fn [s]
         (let [[s w n e] (map #(Double. ^String %) (str/split s #" "))]
           (m/mbr w n e s)))
       (cx/strings-at-path entry-elem [:box])))

(defn xml-elem->shapes
  "Extracts the spatial shapes from the XML entry."
  [entry-elem]
  (when-let [coordinate-system (some-> (cx/string-at-path entry-elem [:coordinateSystem])
                                       str/lower-case
                                       keyword)]
    (let [coordinate-system (if (= coordinate-system :orbit)
                              :geodetic
                              coordinate-system)]
      (->> [xml-elem->polygons-without-holes
            xml-elem->polygons-with-holes
            xml-elem->points
            xml-elem->lines
            xml-elem->bounding-rectangles]
           (mapcat #(% entry-elem))
           (map (partial umm-s/set-coordinate-system coordinate-system))))))

(defn- parse-orbit-params
  "Convert orbit parameter attributes to their proper key /value types"
  [attribs]
  (when (seq attribs)
    (let [{:keys [swathWidth period inclinationAngle numberOfOrbits startCircularLatitude]} attribs]
      (util/remove-nil-keys {:swath-width (when swathWidth
                                            (Double/parseDouble swathWidth))
                             :period (when period
                                       (Double/parseDouble period))
                             :inclination-angle (when inclinationAngle
                                                  (Double/parseDouble inclinationAngle))
                             :number-of-orbits (when numberOfOrbits
                                                 (Double/parseDouble numberOfOrbits))
                             :start-circular-latitude (when startCircularLatitude
                                                        (Double/parseDouble startCircularLatitude))}))))

(defn- parse-orbit-attribs
  "Convert orbit attributes to their proper keys / value types"
  [attribs]
  (when-let [{:keys [ascendingCrossing startLatitude startDirection endLatitude endDirection]}
             (not-empty attribs)]
    {:ascending-crossing (when ascendingCrossing
                           (Double/parseDouble ascendingCrossing))
     :start-lat (when startLatitude
                  (Double/parseDouble startLatitude))
     :start-direction (echo-s/orbit-direction->key startDirection)
     :end-lat (when endLatitude
                (Double/parseDouble endLatitude))
     :end-direction (echo-s/orbit-direction->key endDirection)}))

(defn- parse-ocsd-attribs
  "Convert orbit-calculated-spatial-domains attributes to their proper keys / value types"
  [attribs]
  (let [{:keys [orbitModelName orbitNumber startOrbitNumber stopOrbitNumber
                equatorCrossingLongitude equatorCrossingDateTime]} attribs]
    (util/remove-nil-keys {:orbital-model-name orbitModelName
                           :orbit-number (when orbitNumber
                                           (Integer/parseInt orbitNumber))
                           :start-orbit-number (when startOrbitNumber
                                                 (Integer/parseInt startOrbitNumber))
                           :stop-orbit-number (when stopOrbitNumber
                                                (Integer/parseInt stopOrbitNumber))
                           :equator-crossing-longitude (when equatorCrossingLongitude
                                                         (Double/parseDouble
                                                           equatorCrossingLongitude))
                           :equator-crossing-date-time equatorCrossingDateTime})))

(defn xml-elem->tag
  "Extracts the tag from the XML entry."
  [tag-elem]
  (when-let [tag-key (cx/string-at-path tag-elem [:tagKey])]
    (if-let [tag-data (cx/string-at-path tag-elem [:data])]
      [tag-key {"data" (json/parse-string tag-data)}]
      [tag-key {}])))

(defmulti xml-elem->entry
  "Retrns an atom entry from a parsed atom xml structure"
  (fn [concept-type xml-elem]
    concept-type))

(defmethod xml-elem->entry :collection
  [concept-type entry-elem]
  (util/remove-nil-keys
    {:id (cx/string-at-path entry-elem [:id])
     :consortiums (seq (cx/strings-at-path entry-elem [:consortium]))
     :title (cx/string-at-path entry-elem [:title])
     :updated (cx/string-at-path entry-elem [:updated])
     :dataset-id (cx/string-at-path entry-elem [:datasetId])
     :short-name (cx/string-at-path entry-elem [:shortName])
     :version-id (cx/string-at-path entry-elem [:versionId])
     :summary (cx/string-at-path entry-elem [:summary])
     :original-format (cx/string-at-path entry-elem [:originalFormat])
     :collection-data-type (cx/string-at-path entry-elem [:collectionDataType])
     :data-center (cx/string-at-path entry-elem [:dataCenter])
     :archive-center (cx/string-at-path entry-elem [:archiveCenter])
     :organizations (seq (cx/strings-at-path entry-elem [:organization]))
     :processing-level-id (cx/string-at-path entry-elem [:processingLevelId])
     :links (seq (map :attrs (cx/elements-at-path entry-elem [:link])))
     :start (cx/datetime-at-path entry-elem [:start])
     :end (cx/datetime-at-path entry-elem [:end])
     :associated-difs (seq (cx/strings-at-path entry-elem [:difId]))
     :online-access-flag (cx/bool-at-path entry-elem [:onlineAccessFlag])
     :browse-flag (cx/bool-at-path entry-elem [:browseFlag])
     :coordinate-system (cx/string-at-path entry-elem [:coordinateSystem])
     :orbit-parameters (parse-orbit-params
                         (:attrs (cx/element-at-path
                                   entry-elem
                                   [:orbitParameters])))
     :shapes (seq (xml-elem->shapes entry-elem))
     :score (cx/double-at-path entry-elem [:score])
     :granule-count (cx/long-at-path entry-elem [:granuleCount])
     :has-granules (cx/bool-at-path entry-elem [:hasGranules])
     :has-variables (cx/bool-at-path entry-elem [:hasVariables])
     :has-formats (cx/bool-at-path entry-elem [:hasFormats])
     :has-transforms (cx/bool-at-path entry-elem [:hasTransforms])
     :has-spatial-subsetting (cx/bool-at-path entry-elem [:hasSpatialSubsetting])
     :has-temporal-subsetting (cx/bool-at-path entry-elem [:hasTemporalSubsetting])
     :cloud-hosted (cx/bool-at-path entry-elem [:cloudHosted])
     :tags (when-let [tags (seq (map xml-elem->tag (cx/elements-at-path entry-elem [:tag])))]
             (into {} tags))}))

(defmethod xml-elem->entry :granule
  [concept-type entry-elem]
  (util/remove-nil-keys
    {:id (cx/string-at-path entry-elem [:id])
     :title (cx/string-at-path entry-elem [:title])
     :updated (cx/string-at-path entry-elem [:updated])
     :dataset-id (cx/string-at-path entry-elem [:datasetId])
     :collection-concept-id (cx/string-at-path entry-elem [:collectionConceptId])
     :producer-granule-id (cx/string-at-path entry-elem [:producerGranuleId])
     :size (cx/double-at-path entry-elem [:granuleSizeMB])
     :original-format (cx/string-at-path entry-elem [:originalFormat])
     :data-center (cx/string-at-path entry-elem [:dataCenter])
     :links (seq (map :attrs (cx/elements-at-path entry-elem [:link])))
     :orbit (parse-orbit-attribs (cx/attrs-at-path entry-elem [:orbit]))
     :orbit-calculated-spatial-domains (seq
                                         (map
                                           #(parse-ocsd-attribs (:attrs %))
                                           (cx/elements-at-path
                                             entry-elem
                                             [:orbitCalSpatialDomain])))
     :start (cx/datetime-at-path entry-elem [:start])
     :end (cx/datetime-at-path entry-elem [:end])
     :online-access-flag (cx/bool-at-path entry-elem [:onlineAccessFlag])
     :browse-flag (cx/bool-at-path entry-elem [:browseFlag])
     :day-night-flag (cx/string-at-path entry-elem [:dayNightFlag])
     :cloud-cover (cx/double-at-path entry-elem [:cloudCover])
     :coordinate-system (cx/string-at-path entry-elem [:coordinateSystem])
     :shapes (seq (xml-elem->shapes entry-elem))}))

(defn parse-atom-result
  "Returns an atom result in map from an atom xml"
  [concept-type xml]
  (let [xml-struct (fx/parse-str xml)]
    (util/remove-nil-keys
      {:id (cx/string-at-path xml-struct [:id])
       :title (cx/string-at-path xml-struct [:title])
       :entries (seq (keep (partial xml-elem->entry concept-type)
                           (cx/elements-at-path xml-struct [:entry])))
       :facets (facets/parse-facets-xml (cx/element-at-path xml-struct [:facets]))})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Creating expected atom results

(def resource-type->link-type-uri
  {"GET DATA" "http://esipfed.org/ns/fedsearch/1.1/data#"
   "GET DATA VIA DIRECT ACCESS" "http://esipfed.org/ns/fedsearch/1.1/s3#"
   "USE SERVICE API" "http://esipfed.org/ns/fedsearch/1.1/service#"
   "GET RELATED VISUALIZATION" "http://esipfed.org/ns/fedsearch/1.1/browse#"
   "ALGORITHM INFO" "http://esipfed.org/ns/fedsearch/1.1/documentation#"
   "VIEW PROJECT HOME PAGE" "http://esipfed.org/ns/fedsearch/1.1/metadata#"})

(defn- add-attribs
  "Returns the attribs with the field-value pair added if there is a value"
  [attribs field value]
  (if (nil? value) attribs (assoc attribs field value)))

(defn- related-url->link
  "Returns the atom link of the given related url. Collection's related url uses umm-spec-lib for
  parsing and granule's related url uses umm-lib for parsing and the title is parsed differently."
  [related-url concept-type]
  (let [{:keys [type url title mime-type size inherited]} related-url
        rel (if type
              (resource-type->link-type-uri type "http://esipfed.org/ns/fedsearch/1.1/metadata#")
              "http://esipfed.org/ns/fedsearch/1.1/documentation#") ; The UMM spec default is a doc URL
        attribs (-> {}
                    (add-attribs :inherited inherited)
                    (add-attribs :size size)
                    (add-attribs :rel rel)
                    (add-attribs :type mime-type)
                    (add-attribs :hreflang "en-US")
                    (add-attribs :href url))
        attribs (if (= :granule concept-type)
                  (add-attribs attribs
                               :title
                               (if (= "USE SERVICE API" type)
                                 (str title " (USE SERVICE API : OPENDAP DATA)")
                                 title))
                  attribs)]
    attribs))

(defn- collection-related-urls->links
  "Returns the atom links of the given collection related urls"
  [related-urls]
  (map #(related-url->link % :collection) related-urls))

(defn- granule-related-urls->links
  "Returns the atom links of the given granule related urls.
  Since granule parsing still uses umm-lib, the atom link will have the title populated
  differently from collection's related url which uses umm-spec-lib for parsing."
  [related-urls]
  (map #(related-url->link % :granule) related-urls))

(defn- add-collection-links
  "Returns the related-urls after adding the atom-links in the collection"
  [coll related-urls]
  (let [coll-related-urls (map #(assoc % :title nil) (:related-urls coll))
        non-browse-coll-links (filter #(not= "GET RELATED VISUALIZATION" (:type %)) coll-related-urls)]
    (concat related-urls (map #(assoc % :inherited "true") non-browse-coll-links))))

(defn collection->expected-atom
  "Returns the atom map of the collection. Convert collection to native format metadata and back
  to accurately mimic what ingest does. Do not do the conversion if already umm-json since
  that will do a conversion to echo10"
  [collection]
  (let [{{:keys [short-name version-id processing-level-id collection-data-type]} :product
         :keys [concept-id format-key has-variables has-formats has-transforms
                has-spatial-subsetting has-temporal-subsetting cloud-hosted
                services variables tools]} collection
        collection (data-core/mimic-ingest-retrieve-metadata-conversion collection)
        {:keys [summary entry-title related-urls associated-difs organizations]} collection
        ;; ECSE-158 - We will use UMM-C's DataDates to get insert-time, update-time for DIF9/DIF10.
        ;; DIF9 doesn't support DataDates in umm-spec-lib:
        ;;  So its insert-time and update-time are nil.
        update-time (when-not (= :dif format-key)
                      (get-in collection [:data-provider-timestamps :update-time]))
        spatial-representation (get-in collection [:spatial-coverage :spatial-representation])
        coordinate-system (when spatial-representation
                            (csk/->SCREAMING_SNAKE_CASE_STRING spatial-representation))
        orbit-parameters (get-in collection [:spatial-coverage :orbit-parameters])
        archive-org (first (filter #(= :archive-center (:type %)) organizations))
        archive-center (when archive-org (:org-name archive-org))
        ;; not really fool proof to get start/end datetime, just get by with the current test setting
        {:keys [beginning-date-time ending-date-time]} (first (get-in collection
                                                                      [:temporal :range-date-times]))
        shapes (map (partial umm-s/set-coordinate-system spatial-representation)
                    (get-in collection [:spatial-coverage :geometries]))
        version-id (or version-id eid/DEFAULT_VERSION)
        entry-id (eid/entry-id short-name version-id)
        associated-difs (if (#{:dif :dif10} format-key) [entry-id] associated-difs)
        ;; Remove duplicate archive/distribution center from an echo10 conversion - only need one
        organizations (remove #(and (= archive-center (:org-name %)) (= :distribution-center (:type %))) organizations)
        temporal (:temporal collection)
        associations (when (or (seq services) (seq variables) (seq tools))
                       (util/remove-map-keys empty? {:variables (set variables)
                                                     :services (set services)
                                                     :tools (set tools)}))]
    (util/remove-nil-keys
     {:id concept-id
      :title entry-title
      :summary summary
      :updated (when update-time (str update-time))
      :dataset-id entry-title
      :short-name short-name
      :version-id version-id
      :original-format (atom-results-handler/metadata-format->atom-original-format (name format-key))
      :collection-data-type collection-data-type
      :data-center (:provider-id (cu/parse-concept-id concept-id))
      :archive-center archive-center
      :organizations (seq (map :org-name organizations))
      :processing-level-id processing-level-id
      :start (if temporal
               (sed/start-date :collection temporal)
               ;; no temporal in collection will be treated as start date 1970-01-01T00:00:00 by CMR
               date-util/parsed-default-date)
      :end (some->> temporal (sed/end-date :collection))
      :links (seq (collection-related-urls->links related-urls))
      :coordinate-system coordinate-system
      ;; Need to create UMM OrbitParameters record into map so test comparisons don't fail
      :orbit-parameters (when orbit-parameters (into {} orbit-parameters))
      :shapes (seq shapes)
      :associated-difs associated-difs
      :online-access-flag (not (empty? (ru/downloadable-urls related-urls)))
      :browse-flag (not (empty? (ru/browse-urls related-urls)))
      :has-variables (boolean has-variables)
      :has-formats (boolean has-formats)
      :has-transforms (boolean has-transforms)
      :has-spatial-subsetting (boolean has-spatial-subsetting)
      :has-temporal-subsetting (boolean has-temporal-subsetting)
      :cloud-hosted (boolean cloud-hosted)
      :associations associations})))

(defn- get-coll-service-features
  "Returns the service features in JSON format for the given collection"
  [collection]
  (let [{:keys [opendap esi harmony]} (:service-features collection)]
    {:opendap (merge atom-results-handler/base-has-features opendap)
     :esi (merge atom-results-handler/base-has-features esi)
     :harmony (merge atom-results-handler/base-has-features harmony)}))

(defn collection->expected-json
  "Returns the JSON map of the collection"
  [collection]
  (assoc (collection->expected-atom collection)
         :platforms
         (map :short-name (:platforms collection))
         :service-features
         (get-coll-service-features collection)))

(defn collections->expected-atom
  "Returns the atom map of the collections"
  [collections atom-path]
  (util/remove-nil-keys
    {:id (str (url/search-root) atom-path)
     :title "ECHO dataset metadata"
     :entries (seq (map collection->expected-atom collections))}))

(defn collections->expected-json
  "Returns the JSON map of the collections"
  [collections url-path]
  (util/remove-nil-keys
    {:id (str (url/search-root) url-path)
     :title "ECHO dataset metadata"
     :entries (seq (map collection->expected-json collections))}))

(defn atom-collection-results-match?
  [expected-items atom-results]
  (let [expected-entries (map collection->expected-atom expected-items)]
    (= (set expected-entries)
       (set (map #(dissoc % :granule-count)
                 (get-in atom-results [:results :entries]))))))

(defn json-collection-results-match?
  [expected-items json-results]
  (let [expected-entries (map collection->expected-json expected-items)]
    (= (set expected-entries)
       (set (map #(dissoc % :granule-count)
                 (get-in json-results [:results :entries]))))))

(defn granule->expected-atom
  "Returns the atom map of the granule"
  [granule coll]
  (let [{:keys [concept-id granule-ur producer-gran-id size related-urls
                beginning-date-time ending-date-time single-date-time day-night cloud-cover format-key
                orbit-calculated-spatial-domains]} granule
        coll-concept-id (:concept-id coll)
        related-urls (add-collection-links coll related-urls)
        dataset-id (get-in granule [:collection-ref :entry-title])
        update-time (get-in granule [:data-provider-timestamps :update-time])
        granule-spatial-representation (get-in coll [:spatial-coverage :granule-spatial-representation])
        coordinate-system (when granule-spatial-representation
                            (csk/->SCREAMING_SNAKE_CASE_STRING granule-spatial-representation))
        orbit (get-in granule [:spatial-coverage :orbit])
        granule-shapes (map (partial umm-s/set-coordinate-system granule-spatial-representation)
                            (get-in granule [:spatial-coverage :geometries]))
        shapes (concat granule-shapes (dg/granule->orbit-shapes granule coll))]
    (util/remove-nil-keys
     {:id concept-id
      :title granule-ur
      :dataset-id dataset-id
      :collection-concept-id coll-concept-id
      :producer-granule-id producer-gran-id
      :updated (str update-time)
      :coordinate-system (or coordinate-system "NO_SPATIAL") ; UMM-C defaults to NO_SPATIAL
      :size size
      :original-format (atom-results-handler/metadata-format->atom-original-format (name format-key))
      :data-center (:provider-id (cu/parse-concept-id concept-id))
      :links (seq (granule-related-urls->links related-urls))
      :orbit (when orbit (into {} orbit))
      :orbit-calculated-spatial-domains (seq orbit-calculated-spatial-domains)
      :start (some->> (:temporal granule) (sed/start-date :granule))
      :end (some->> (:temporal granule) (sed/end-date :granule))
      :online-access-flag (not (empty? (ru/downloadable-urls related-urls)))
      :browse-flag (not (empty? (ru/browse-urls related-urls)))
      :day-night-flag day-night
      :cloud-cover cloud-cover
      :shapes (seq shapes)})))

(defn granules->expected-atom
  "Returns the atom map of the granules"
  [granules collections atom-path]
  (util/remove-nil-keys
    {:id (str (url/search-root) atom-path)
     :title "ECHO granule metadata"
     :entries (seq (map granule->expected-atom granules collections))}))
