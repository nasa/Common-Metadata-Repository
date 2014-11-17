(ns cmr.umm.test.generators.collection
  "Provides clojure.test.check generators for use in testing other projects."
  (:require [clojure.test.check.generators :as gen]
            [cmr.common.test.test-check-ext :as ext-gen]
            [cmr.umm.collection :as c]
            [cmr.umm.test.generators.collection.temporal :as t]
            [cmr.umm.test.generators.collection.science-keyword :as sk]
            [cmr.umm.test.generators.collection.product-specific-attribute :as psa]
            [cmr.umm.test.generators.spatial :as spatial-gen]
            [cmr.spatial.test.generators :as sgen]))

(def short-names
  (ext-gen/string-alpha-numeric 1 10))

(def version-ids
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
                     (ext-gen/optional processing-level-ids)
                     (ext-gen/optional collection-data-types)))

(def data-provider-timestamps
  (ext-gen/model-gen c/->DataProviderTimestamps ext-gen/date-time ext-gen/date-time (ext-gen/optional ext-gen/date-time)))

(def entry-ids
  (ext-gen/string-alpha-numeric 1 10))

(def entry-titles
  (ext-gen/string-alpha-numeric 1 10))

(def summary
  (ext-gen/string-alpha-numeric 1 10))

(def sensor-techniques
  (ext-gen/string-ascii 1 10))

(def sensor-long-names
  (ext-gen/string-ascii 1 10))

(def sensor-short-names
  (ext-gen/string-ascii 1 10))

(def sensors
  (ext-gen/model-gen c/->Sensor sensor-short-names
                     (ext-gen/optional sensor-long-names)
                     (ext-gen/optional sensor-techniques)))

(def instrument-techniques
  (ext-gen/string-ascii 1 10))

(def instrument-long-names
  (ext-gen/string-ascii 1 10))

(def instrument-short-names
  (ext-gen/string-ascii 1 10))

(def instruments
  (ext-gen/model-gen c/->Instrument
                     instrument-short-names
                     (ext-gen/optional instrument-long-names)
                     (ext-gen/optional instrument-techniques)
                     (ext-gen/nil-if-empty (gen/vector sensors 0 4))))


(def characterstic-values
  (ext-gen/string-ascii 1 10))

(def characterstic-units
  (ext-gen/string-ascii 1 10))

(def characterstic-datatypes
  (ext-gen/string-ascii 1 10))

(def characteric-descriptions
  (ext-gen/string-ascii 1 10))

(def characteric-names
  (ext-gen/string-ascii 1 10))

(def characterstics
  (ext-gen/model-gen c/->Characteristic characteric-names
                     characteric-descriptions
                     characterstic-datatypes
                     characterstic-units
                     characterstic-values))

(def phones
  (ext-gen/model-gen c/->Phone (ext-gen/string-ascii 1 23)
                    (ext-gen/string-ascii 1 30)))

(def addresses
  (ext-gen/model-gen c/->Address (ext-gen/string-ascii 1 30)
                     (ext-gen/string-ascii 1 30)
                     (ext-gen/string-ascii 1 10)
                     (ext-gen/string-ascii 1 30)
                     (gen/vector (ext-gen/string-ascii 1 30) 1 2)))

(def emails
  (ext-gen/string-ascii 1 1024))

(def contact-persons
  (ext-gen/model-gen c/->ContactPerson (gen/elements ["Investigator"
                                                "Technical Contact"
                                                "Metadata Author"])
                     (gen/vector addresses 1 3)
                     (gen/vector emails 0 4)
                     (ext-gen/string-ascii 1 30)
                     (ext-gen/string-ascii 1 30)
                     (ext-gen/string-ascii 1 30)
                     (gen/vector phones 1 3)))

(def platform-short-names
  (ext-gen/string-ascii 1 10))

(def platform-long-names
  (ext-gen/string-ascii 1 10))

(def platform-types
  (ext-gen/string-ascii 1 10))

(def platforms
  (ext-gen/model-gen c/->Platform
                     platform-short-names
                     platform-long-names
                     platform-types
                     (ext-gen/nil-if-empty (gen/vector instruments 0 4))
                     (ext-gen/nil-if-empty (gen/vector characterstics 0 4))))

(def campaign-short-names
  (ext-gen/string-ascii 1 10))

(def campaign-long-names
  (ext-gen/string-ascii 1 10))

(def campaigns
  (ext-gen/model-gen c/->Project campaign-short-names (ext-gen/optional campaign-long-names)))

(def two-d-names
  (ext-gen/string-ascii 1 10))

(def two-d-coordinate-systems
  (ext-gen/model-gen c/->TwoDCoordinateSystem two-d-names))

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

(def related-url
  (gen/fmap (fn [[type url description size]]
              (if (= type "GET RELATED VISUALIZATION")
                (c/map->RelatedURL {:url url
                                    :type type
                                    :description description
                                    :title description
                                    :size size})
                (c/map->RelatedURL {:url url
                                    :type type
                                    :description description
                                    :title description})))
            (gen/tuple related-url-types
                       ext-gen/file-url-string
                       (ext-gen/string-ascii 1 10)
                       gen/s-pos-int)))

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

(def collections
  (gen/fmap (fn [[attribs proc-org archive-org dist-org]]
              (let [product (:product attribs)]
                (c/map->UmmCollection (assoc attribs
                                             :organizations (seq (remove nil? (flatten [proc-org archive-org dist-org])))))))
            (gen/tuple
              (gen/hash-map
                :entry-id entry-ids
                :entry-title entry-titles
                :summary summary
                :product products
                :access-value (ext-gen/optional (ext-gen/choose-double -10 10))
                :data-provider-timestamps data-provider-timestamps
                :temporal t/temporals
                :spatial-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                :temporal-keywords (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                ;; DIF requires science-keyowrds to always exist, not ideal here for ECHO10. As science-keywords is optional for ECHO10
                :science-keywords (gen/vector sk/science-keywords 1 3)
                :personnel (gen/vector contact-persons 1 3)
                :platforms (ext-gen/nil-if-empty (gen/vector platforms 0 4))
                :product-specific-attributes (ext-gen/nil-if-empty (gen/vector psa/product-specific-attributes 0 10))
                :projects (ext-gen/nil-if-empty (gen/vector campaigns 0 4))
                :two-d-coordinate-systems (ext-gen/nil-if-empty (gen/vector two-d-coordinate-systems 0 3))
                :related-urls (ext-gen/nil-if-empty (gen/vector related-url 0 5))
                :associated-difs (ext-gen/nil-if-empty (gen/vector (ext-gen/string-alpha-numeric 1 10) 0 4))
                :spatial-coverage (ext-gen/optional spatial-coverages))
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
