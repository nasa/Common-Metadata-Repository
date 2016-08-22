(ns cmr.umm.test.generators.collection
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.umm-collection :as c]
            [cmr.umm.test.generators.collection.temporal :as t]
            [cmr.umm.test.generators.collection.science-keyword :as sk]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]
            [cmr.umm.test.generators.spatial :as spatial-gen]
            [cmr.spatial.test.generators :as sgen]))

(def optional-short-string (ext-gen/optional (ext-gen/string-ascii 1 10)))

(def optional-url (ext-gen/optional ext-gen/file-url-string))

(def optional-number (ext-gen/optional (gen/choose 1 1000)))

(def short-names
  (ext-gen/string-alpha-numeric 1 10))

(def version-ids
  (ext-gen/string-alpha-numeric 1 10))

(def version-descriptions
  (ext-gen/string-alpha-numeric 1 10))

(def long-names
  (ext-gen/string-alpha-numeric 1 10))

(def processing-level-ids
  (ext-gen/string-alpha-numeric 1 10))

(def collection-data-types
  (gen/elements ["SCIENCE_QUALITY" "NEAR_REAL_TIME" "OTHER"]))

(def products
  (ext-gen/model-gen c/->Product
                     short-names
                     long-names
                     version-ids
                     (ext-gen/optional version-descriptions)
                     (ext-gen/optional processing-level-ids)
                     (ext-gen/optional collection-data-types)))

(def data-provider-timestamps
  (ext-gen/model-gen c/->DataProviderTimestamps
                     ext-gen/date-time ext-gen/date-time (ext-gen/optional ext-gen/date-time) ext-gen/date-time))

(def entry-ids
  (ext-gen/string-alpha-numeric 1 10))

(def entry-titles
  (ext-gen/string-alpha-numeric 1 10))

(def summary
  (ext-gen/string-alpha-numeric 1 10))

(def purpose
  (ext-gen/string-alpha-numeric 1 10))

(def characteristic-values
  (ext-gen/string-ascii 1 10))

(def characteristic-units
  (ext-gen/string-ascii 1 10))

(def characteristic-datatypes
  (ext-gen/string-ascii 1 10))

(def characteric-descriptions
  (ext-gen/string-ascii 1 10))

(def characteric-names
  (ext-gen/string-ascii 1 10))

(def characteristics
  (ext-gen/model-gen c/->Characteristic characteric-names
                     characteric-descriptions
                     characteristic-datatypes
                     characteristic-units
                     characteristic-values))

(def sensor-techniques
  (ext-gen/string-ascii 1 10))

(def sensor-long-names
  (ext-gen/string-ascii 1 10))

(def sensor-short-names
  (ext-gen/string-ascii 1 10))

(def sensors
  (ext-gen/model-gen c/->Sensor sensor-short-names
                     (ext-gen/optional sensor-long-names)
                     (ext-gen/optional sensor-techniques)
                     (ext-gen/nil-if-empty (gen/vector characteristics 0 4))))

(def instrument-techniques
  (ext-gen/string-ascii 1 10))

(def instrument-long-names
  (ext-gen/string-alpha-numeric 1 10))

(def instrument-short-names
  (ext-gen/string-alpha-numeric 1 10))

(def operation-modes
  (ext-gen/string-ascii 1 10))

(def instruments
  (ext-gen/model-gen c/->Instrument
                     instrument-short-names
                     (ext-gen/optional instrument-long-names)
                     (ext-gen/optional instrument-techniques)
                     (ext-gen/nil-if-empty (gen/vector sensors 0 4))
                     (ext-gen/nil-if-empty (gen/vector characteristics 0 4))
                     (ext-gen/nil-if-empty (gen/vector operation-modes 0 4))))

(def platform-short-names
  (ext-gen/string-alpha-numeric 1 10))

(def platform-long-names
  (ext-gen/string-alpha-numeric 1 10))

(def platform-types
  (ext-gen/string-alpha-numeric 1 10))

(def platforms
  (ext-gen/model-gen c/->Platform
                     platform-short-names
                     platform-long-names
                     platform-types
                     (ext-gen/nil-if-empty (gen/vector instruments 0 4))
                     (ext-gen/nil-if-empty (gen/vector characteristics 0 4))))

(def ca-short-names
  (ext-gen/string-ascii 1 10))

(def ca-version-ids
  (ext-gen/string-ascii 1 10))

(def collection-associations
  (ext-gen/model-gen c/->CollectionAssociation ca-short-names ca-version-ids))

(def campaign-short-names
  (ext-gen/string-ascii 1 10))

(def campaign-long-names
  (ext-gen/string-ascii 1 10))

(def campaigns
  (ext-gen/model-gen c/->Project campaign-short-names (ext-gen/optional campaign-long-names)))

(def two-d-names
  (ext-gen/string-ascii 1 10))

(def coordinates
  (let [coords-gen (gen/fmap sort (gen/vector (ext-gen/choose-double 0 1000) 0 2))]
    (ext-gen/model-gen (fn [[min-value max-value]]
                         (when (or min-value max-value)
                           (c/->Coordinate min-value max-value)))
                       coords-gen)))

(def two-d-coordinate-systems
  (ext-gen/model-gen c/->TwoDCoordinateSystem two-d-names coordinates coordinates))

(def org-names
  (ext-gen/string-ascii 1 10))

(def archive-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :archive-center) org-names))

(def processing-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :processing-center) org-names))

(def distribution-center-organizations
  (ext-gen/model-gen c/->Organization (gen/return :distribution-center) org-names))

(def related-url-types
  (gen/elements ["GET DATA" "GET RELATED VISUALIZATION" "VIEW RELATED INFORMATION"]))

(def related-url-mime-types
  (gen/elements ["application/json" "text/csv" "text/xml"]))

(def related-url
  (gen/fmap (fn [[type url description size mime-type]]
              (if (= type "GET RELATED VISUALIZATION")
                (c/map->RelatedURL {:url url
                                    :type type
                                    :description description
                                    :title description
                                    :size size
                                    :mime-type mime-type})
                (c/map->RelatedURL {:url url
                                    :type type
                                    :description description
                                    :title description
                                    :mime-type mime-type})))
            (gen/tuple related-url-types
                       ext-gen/file-url-string
                       (ext-gen/string-ascii 1 10)
                       gen/s-pos-int
                       related-url-mime-types)))

(def orbit-params
  (gen/fmap (fn [[swath-width period incl-angle num-orbits start-clat]]
              (c/->OrbitParameters swath-width period incl-angle num-orbits (when start-clat)))
            (gen/tuple (ext-gen/choose-double 1 100)
                       (ext-gen/choose-double 10 7200)
                       (ext-gen/choose-double -90 90)
                       (ext-gen/choose-double 1 20)
                       (ext-gen/optional sgen/lats))))

(def spatial-coverages
  (gen/fmap (fn [[gsr sr geoms orbit-params]]
              (c/->SpatialCoverage gsr (when geoms sr) geoms orbit-params))
            (gen/tuple (gen/elements c/granule-spatial-representations)
                       (gen/elements c/spatial-representations)
                       (ext-gen/optional (gen/vector spatial-gen/geometries 1 5))
                       orbit-params)))

(def person-names
  (ext-gen/string-alpha-numeric 1 20))

(def contacts
  (gen/fmap (fn [[type value]]
              (c/->Contact type value))
            (gen/tuple (gen/elements [:email :phone :fax])
                       (ext-gen/string-ascii 10 30))))

(def personnels
  (gen/fmap (fn [[first-name middle-name last-name roles contacts]]
              (c/->Personnel first-name middle-name last-name roles contacts))
            (gen/tuple person-names
                       (ext-gen/optional person-names)
                       person-names
                       (gen/vector (ext-gen/string-alpha-numeric 1 20) 1 3)
                       (gen/vector contacts 0 3))))

(def publication-references
  (gen/fmap (fn [ref-map]
              (c/map->PublicationReference ref-map))
            (gen/hash-map
              :author optional-short-string
              :publication-date optional-short-string
              :title optional-short-string
              :series optional-short-string
              :edition optional-short-string
              :volume optional-short-string
              :issue optional-short-string
              :report-number optional-short-string
              :publication-place optional-short-string
              :publisher optional-short-string
              :pages optional-short-string
              :isbn optional-short-string
              :doi optional-short-string
              :related-url optional-url
              :other-reference-details optional-short-string)))

(def collection-progress
  (ext-gen/optional (gen/elements c/collection-progress-states)))

(def collections
  (gen/fmap (fn [[attribs proc-org archive-org dist-org]]
              (let [product (:product attribs)]
                (c/map->UmmCollection (assoc attribs
                                             :organizations (seq (remove nil? (flatten [proc-org archive-org dist-org])))))))
            (gen/tuple
              (gen/hash-map
                :entry-title entry-titles
                :summary summary
                :purpose purpose
                :product products
                :access-value (ext-gen/optional (ext-gen/choose-double -10 10))
                :metadata-language (ext-gen/optional (ext-gen/string-ascii 1 10))
                :quality (ext-gen/optional (ext-gen/string-ascii 1 10))
                :use-constraints (ext-gen/optional (ext-gen/string-ascii 1 10))
                :data-provider-timestamps data-provider-timestamps
                :temporal t/temporals
                :spatial-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                :temporal-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                :science-keywords (ext-gen/nil-if-empty (gen/vector sk/science-keywords 0 3))
                :platforms (ext-gen/nil-if-empty (gen/vector platforms 0 4))
                :product-specific-attributes (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
                :collection-associations (ext-gen/nil-if-empty (gen/vector collection-associations 0 4))
                :projects (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
                :two-d-coordinate-systems (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
                :related-urls (ext-gen/nil-if-empty (gen/vector related-url 0 5))
                :associated-difs (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                :spatial-coverage (ext-gen/optional spatial-coverages)
                :publication-references (ext-gen/nil-if-empty (gen/vector publication-references 0 3))
                :personnel (ext-gen/nil-if-empty (gen/vector personnels 0 3))
                :collection-citations (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 3))
                :collection-progress collection-progress)
              (ext-gen/optional processing-center-organizations)
              (ext-gen/optional archive-center-organizations)
              (gen/vector distribution-center-organizations 1 3))))

; Generator for basic collections that only have the bare minimal fields
;; DEPRECATED - this will go away in the future. Don't use it.
(def basic-collections
  (gen/fmap (fn [[entry-title product]]
              (let [entry-id (str (:short-name product) "_" (:version-id product))]
                (c/map->UmmCollection
                  {:entry-id entry-id
                   :entry-title entry-title
                   :product product})))
            (gen/tuple entry-titles products)))
