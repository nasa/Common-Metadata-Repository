(ns cmr.umm.test.iso-smap.iso-smap-collection-tests
  "Tests parsing and generating SMAP ISO Collection XML."
  (:require [clojure.test :refer :all]
            [cmr.common.test.test-check-ext :refer [defspec]]
            [clojure.test.check.properties :refer [for-all]]
            [clojure.test.check.generators :as gen]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [cmr.common.joda-time]
            [cmr.common.date-time-parser :as p]
            [cmr.spatial.mbr :as mbr]
            [cmr.umm.test.generators.collection :as coll-gen]
            [cmr.umm.iso-smap.iso-smap-collection :as c]
            [cmr.umm.echo10.echo10-collection :as echo10-c]
            [cmr.umm.echo10.echo10-core :as echo10]
            [cmr.umm.umm-collection :as umm-c]
            [cmr.umm.umm-spatial :as umm-s]
            [cmr.umm.iso-smap.iso-smap-core :as iso]
            [clj-time.format :as f]
            [cmr.umm.test.echo10.echo10-collection-tests :as test-echo10]
            [cmr.common.test.test-check-ext :as ext :refer [checking]]))

(defn- spatial-coverage->expected-parsed
  "Returns the expected parsed spatial-coverage for the given spatial-coverage"
  [{:keys [geometries]}]
  (when-let [bounding-boxes (seq (filter #(instance? cmr.spatial.mbr.Mbr %) geometries))]
    (umm-c/map->SpatialCoverage
     {:granule-spatial-representation :geodetic
      :spatial-representation :geodetic
      :geometries bounding-boxes})))

(defn- platform->expected-parsed
  "Returns the expected parsed platform for the given platform."
  [instruments platform]
  (assoc platform :type "Spacecraft" :instruments instruments :characteristics nil))

(defn- platforms->expected-parsed
  "Returns the expected parsed platforms for the given platforms."
  [platforms]
  (let [{:keys [instruments]} (first platforms)
        ;; SMAP ISO does not support instrument technique, sensors, characteristics or operation modes
        instruments (seq (map #(assoc % :technique nil :sensors nil :characteristics nil :operation-modes nil)
                              instruments))]
    (seq (map (partial platform->expected-parsed instruments) platforms))))

(defn- filter-center-type
  "Filters a list of organizations to the given type."
  [orgs org-type]
  (filter #(= org-type (:type %)) orgs))

(defn- centers->personnel
  "Create Personnel records for the given centers."
  [centers role]
  (map (fn [center]
         (umm-c/map->Personnel
           {:last-name (:org-name center)
            :roles [role]}))
       centers))

(defn- collection->personnel
  "Creates personnel from the distribution center contacts."
  [coll]
  (let [orgs (:organizations coll)
        distrib-centers (filter-center-type orgs :archive-center)
        processing-centers (filter-center-type orgs :processing-center)]
    (not-empty
      (concat (centers->personnel processing-centers "originator")
              (centers->personnel distrib-centers "distributor")))))


(defn- umm->expected-parsed-smap-iso
  "Modifies the UMM record for testing SMAP ISO. ISO contains a subset of the total UMM fields
  so certain fields are removed for comparison of the parsed record"
  [coll]
  (let [{{:keys [short-name long-name version-id]} :product
         :keys [entry-title spatial-coverage associated-difs]} coll
        range-date-times (get-in coll [:temporal :range-date-times])
        single-date-times (get-in coll [:temporal :single-date-times])
        temporal (if (seq range-date-times)
                   (umm-c/map->Temporal {:range-date-times range-date-times
                                         :single-date-times []
                                         :periodic-date-times []})
                   (when (seq single-date-times)
                     (umm-c/map->Temporal {:range-date-times []
                                           :single-date-times single-date-times
                                           :periodic-date-times []})))
        personnel (collection->personnel coll)
        organizations (seq (filter #(not (= :distribution-center (:type %))) (:organizations coll)))
        org-name (some :org-name organizations)
        contact-name (or org-name "undefined")
        associated-difs (when (first associated-difs) [(first associated-difs)])]
    (-> coll
        ;; SMAP ISO does not have collection-data-type
        (assoc-in [:product :collection-data-type] nil)
        ;; SMAP ISO does not have processing-level-id
        (assoc-in [:product :processing-level-id] nil)
        ;; There is no delete-time in SMAP ISO
        (assoc-in [:data-provider-timestamps :delete-time] nil)
        ;; SMAP uses date instead of date-time. The hours, minutes and seconds are truncated.
        (update-in [:data-provider-timestamps :revision-date-time]
                   (fn [date-time]
                     (->> date-time
                          (f/unparse (f/formatters :date))
                          (f/parse (f/formatters :date)))))
        ;; SMAP ISO does not have periodic-date-times
        (assoc :temporal temporal)
        ;; SMAP ISO does not have distribution centers as Organization
        (assoc :organizations organizations)
        ;; SMAP ISO only has one dif-id
        (assoc :associated-difs associated-difs)
        ;; SMAP ISO spatial only has BoundingBox
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        ;; SMAP ISO does not support RestrictionFlag
        (dissoc :access-value)
        ;; SMAP ISO does not support UseConstraints
        (dissoc :use-constraints)
        ;; SMAP ISO does not support SpatialKeywords
        (dissoc :spatial-keywords)
        ;; SMAP ISO does not support TemporalKeywords
        (dissoc :temporal-keywords)
        ;; SMAP ISO platform does not have characteristics field
        ;; and instruments are the same for all platforms
        (update-in [:platforms] platforms->expected-parsed)
        ;; SMAP ISO does not support Projects
        (dissoc :projects)
        ;; SMAP ISO does not support AdditionalAttributes
        (dissoc :product-specific-attributes)
        ;; SMAP ISO does not support CollectionAssociations
        (dissoc :collection-associations)
        (dissoc :collection-citations)
        ;; SMAP ISO does not support RelatedURLs
        (dissoc :related-urls)
        ;; SMAP ISO does not support two-d-coordinate-systems
        (dissoc :two-d-coordinate-systems)
        ;; SMAP ISO does not support quality
        (dissoc :quality)
        ;; We don't write out personnel entries when generating SMAP XML
        (assoc :personnel personnel)
        ;; to be supported later
        (dissoc :publication-references)
        umm-c/map->UmmCollection)))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(defspec generate-and-parse-collection-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-smap-iso collection)]
      (= parsed expected-parsed))))

(deftest generate-and-parse-collection-between-formats-test
  (checking "iso smap parse between formats" 100
    [collection coll-gen/collections]
    (let [xml (iso/umm->iso-smap-xml collection)
          parsed-iso (c/parse-collection xml)
          echo10-xml (echo10/umm->echo10-xml parsed-iso)
          parsed-echo10 (echo10-c/parse-collection echo10-xml)
          expected-parsed (test-echo10/umm->expected-parsed-echo10 (umm->expected-parsed-smap-iso collection))]
      (is (= parsed-echo10 expected-parsed))
      (is (= 0 (count (echo10-c/validate-xml echo10-xml)))))))

(def sample-collection-xml
  (slurp (io/file (io/resource "data/iso_smap/sample_smap_iso_collection.xml"))))

(def expected-temporal
  (umm-c/map->Temporal
    {:range-date-times
     [(umm-c/map->RangeDateTime
        {:beginning-date-time (p/parse-datetime "2014-10-31T00:00:00.000Z")
         :ending-date-time (p/parse-datetime "2018-01-31T00:00:00.000Z")})]
     :single-date-times []
     :periodic-date-times []}))

(def expected-collection
  (umm-c/map->UmmCollection
    {:entry-title "SMAP Collection Dataset ID"
     :summary "Parsed high resolution and low resolution radar instrument telemetry with spacecraft position, attitude and antenna azimuth information as well as voltage and temperature sensor measurements converted from telemetry data numbers to engineering units."
     :purpose "This product provides representative L-band radar cross section measures over all land surfaces except Antarctica and coastal oceans within 1000 km of land. The SMAP project will use these data to determine freeze-thaw state, ascertain the location of temporary water bodies and calculate vegetation index. The SMAP project will also use these data to improve the resolution of soil moisture retrieved from radiometer measures."
     :metadata-language "eng"
     :product (umm-c/map->Product
                {:short-name "SPL1AA"
                 :long-name "SMAP Level 1A Parsed Radar Instrument Telemetry"
                 :version-id "002"
                 :version-description "The initial version of the Level 1A Radar executable."})
     :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                 {:insert-time (p/parse-datetime "2013-04-04T15:15:00Z")
                                  :update-time (p/parse-datetime "2013-04-05T17:15:00Z")
                                  :revision-date-time (p/parse-datetime "2013-01-02")})
     :temporal expected-temporal
     :science-keywords [(umm-c/map->ScienceKeyword
                          {:category "EARTH SCIENCE"
                           :topic "SPECTRAL/ENGINEERING"
                           :term "MICROWAVE"
                           :variable-level-1 "BRIGHTNESS TEMPERATURE"})
                        (umm-c/map->ScienceKeyword
                          {:category "EARTH SCIENCE"
                           :topic "ATMOSPHERE"
                           :term "CLOUDS"
                           :variable-level-1 "TROPOSPHERIC/LOW LEVEL CLOUDS (OBSERVED/ANALYZED)"
                           :variable-level-2 "FOG"
                           :variable-level-3 "ICE FOG"})
                        (umm-c/map->ScienceKeyword
                          {:category "EARTH SCIENCE SERVICES"
                           :topic "BIOSPHERE"
                           :term "VEGETATION"
                           :variable-level-1 "PLANT CHARACTERISTICS"
                           :variable-level-2 "VEGETATION WATER CONTENT"})]
     :platforms [(umm-c/map->Platform
                   {:short-name "SMAP"
                    :long-name "Soil Moisture Active and Passive Observatory"
                    :type "Spacecraft"
                    :instruments [(umm-c/map->Instrument
                                    {:short-name "SMAP L-BAND RADAR"
                                     :long-name "SMAP L-Band Radar"})
                                  (umm-c/map->Instrument
                                    {:short-name "SMAP L-BAND RADIOMETER"
                                     :long-name "SMAP L-Band Radiometer"})]})]
     :spatial-coverage (umm-c/map->SpatialCoverage
                         {:granule-spatial-representation :geodetic
                          :spatial-representation :geodetic
                          :geometries [(mbr/mbr -180.0 87.0 180.0 -87.0)]})
     :associated-difs ["A_DIF_ID"]
     :organizations
     [(umm-c/map->Organization
        {:type :processing-center
         :org-name "Jet Propulsion Laboratory"})
      (umm-c/map->Organization
        {:type :archive-center
         :org-name "Alaska Satellite Facility"})]
     :personnel [(umm-c/map->Personnel
                   {:first-name nil
                    :middle-name nil
                    :last-name "National Aeronautics and Space Administration (NASA)"
                    :roles ["resourceProvider"]
                    :contacts nil})
                 (umm-c/map->Personnel
                   {:first-name nil
                    :middle-name nil
                    :last-name "Jet Propulsion Laboratory"
                    :roles ["originator"]
                    :contacts nil})
                 (umm-c/map->Personnel
                   {:first-name nil
                    :middle-name nil
                    :last-name "Alaska Satellite Facility"
                    :roles ["distributor"]
                    :contacts nil})]
     :collection-progress :in-work}))

(deftest parse-collection-test
  (testing "parse collection"
    (is (= expected-collection (c/parse-collection sample-collection-xml))))
  (testing "parse temporal"
    (is (= expected-temporal (c/parse-temporal sample-collection-xml)))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml sample-collection-xml)))))
  (testing "invalid xml"
    (is (= [(str "Exception while parsing invalid XML: Line 6 - cvc-complex-type.2.4.a: Invalid content was found "
                 "starting with element 'gmd:XXXX'. One of "
                 "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":language, "
                 "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                 "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                 "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]
           (c/validate-xml (s/replace sample-collection-xml "fileIdentifier" "XXXX"))))))
