(ns cmr.umm.test.iso-mends.iso-mends-collection-tests
  "Tests parsing and generating ISO Collection XML."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as s]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as p]
   [cmr.common.joda-time]
   [cmr.common.test.test-check-ext :refer [defspec checking]]
   ;; this is not needed until the ECHO to ISO XSLT is fixed
   ;; [cmr.common.xml.xslt :as xslt]
   [cmr.spatial.derived :as d]
   [cmr.spatial.encoding.gmd :as gmd]
   [cmr.spatial.relations :as r]
   [cmr.umm.echo10.collection.personnel :as echo-pe]
   [cmr.umm.echo10.echo10-collection :as echo10-c]
   [cmr.umm.echo10.echo10-core :as echo10]
   [cmr.umm.iso-mends.iso-mends-collection :as c]
   [cmr.umm.iso-mends.iso-mends-core :as iso]
   [cmr.umm.test.echo10.echo10-collection-tests :as test-echo10]
   [cmr.umm.test.generators.collection :as coll-gen]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-spatial :as umm-s]))

(defn- fix-mbr
  "The mbr creation in the functions below sometimes gets a rounding error when using
  calculate-derived. This function fixes that since the rounding error occurs
  in :center-point."
  [mbr]
  (cmr.spatial.mbr/mbr
   (:west mbr) (:north mbr) (:east mbr) (:south mbr)))

(defmulti create-bounding-box
  (fn [geometry]
    (type geometry)))

(defmethod create-bounding-box cmr.spatial.mbr.Mbr
  [geometry]
  geometry)

(defmethod create-bounding-box cmr.spatial.point.Point
  [geometry]
  (cmr.spatial.mbr/point->mbr geometry))

(defmethod create-bounding-box cmr.spatial.line_string.LineString
  [geometry]
  (fix-mbr (:mbr (d/calculate-derived geometry))))

(defmethod create-bounding-box cmr.spatial.polygon.Polygon
  [geometry]
  (fix-mbr (:mbr (d/calculate-derived geometry))))

(defn- add-redundant-bounding-boxes
  "ISO generates redundant bounding boxes. We are no longer removing them. In the
  expected conversion we need to generate redundant bounding boxes for each geomtry
  type."
  [spatial-coverage]
  (if-let [geometries (:geometries spatial-coverage)]
    (assoc spatial-coverage :geometries
      (interleave
       (map create-bounding-box geometries)
       geometries))
    spatial-coverage))

(defn- spatial-coverage->expected-parsed
  "Returns the expected parsed ISO MENDS SpatialCoverage from a UMM collection."
  [{:keys [geometries spatial-representation granule-spatial-representation]}]
  (when (or granule-spatial-representation (seq geometries))
    (umm-c/map->SpatialCoverage
      {:spatial-representation spatial-representation
       :granule-spatial-representation (or granule-spatial-representation :no-spatial)
       :geometries (seq (map #(umm-s/set-coordinate-system spatial-representation %) geometries))})))

(defn- related-urls->expected-parsed
  "Returns the expected parsed related-urls for the given related-urls."
  [related-urls]
  (seq (map #(assoc % :size nil) related-urls)))

(defn- sensors->expected-parsed
  "Return the expected parsed sensors for the given sensors."
  [sensors]
  (seq (map #(assoc % :technique nil :characteristics nil) sensors)))

(defn- instrument->expected-parsed
  "Return the expected parsed instrument for the given instrument."
  [instrument]
  (-> instrument
      ;; ISO does not support instrument technique, characteristics or operation modes
      (assoc :technique nil :characteristics nil :operation-modes nil)
      (update-in [:sensors] sensors->expected-parsed)))

(defn- instruments->expected-parsed
  "Return the expected parsed instruments for the given instruments."
  [instruments]
  (seq (map instrument->expected-parsed instruments)))

(defn- platform->expected-parsed
  "Return the expected parsed platform for the given platform."
  [platform]
  (let [instruments (:instruments platform)]
    (-> platform
        (assoc :characteristics nil)
        (assoc :instruments (instruments->expected-parsed instruments)))))

(defn- platforms->expected-parsed
  "Returns the expected parsed platforms for the given platforms."
  [platforms]
  (seq (map platform->expected-parsed platforms)))

(defn- related-urls->expected-parsed
  "Returns the expected parsed related-urls for the given related-urls."
  [related-urls]
  (seq (map #(assoc % :size nil :mime-type nil) related-urls)))

(defn- collection->personnel
  "Creates personnel from the distribution center contacts."
  [coll]
  (let [distrib-centers (filter #(= :archive-center (:type %)) (:organizations coll))]
    (map (fn [distrib-center]
           (umm-c/map->Personnel
             {:last-name (:org-name distrib-center)
              :roles ["distributor"]}))
         distrib-centers)))

(defn umm->expected-parsed-iso
  "Modifies the UMM record for testing ISO. ISO contains a subset of the total UMM fields so certain
  fields are removed for comparison of the parsed record"
  [coll]
  (let [{:keys [spatial-coverage]} coll
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
        revision-date-time (get-in coll [:data-provider-timestamps :revision-date-time])
        personnel (not-empty (collection->personnel coll))
        organizations (seq (filter #(not (= :distribution-center (:type %))) (:organizations coll)))]
    (-> coll
        ;; ISO does not have version-description
        (assoc-in [:product :version-description] nil)
        ;; ISO does not have collection-data-type
        (assoc-in [:product :collection-data-type] nil)
        ;; There is no delete-time in ISO
        (assoc-in [:data-provider-timestamps :delete-time] nil)
        ;; Revision date time is same as update-time
        (assoc-in [:data-provider-timestamps :update-time] revision-date-time)
        ;; ISO does not have periodic-date-times
        (assoc :temporal temporal)
        ;; ISO does not support mime-type in RelatedURLs
        (update-in [:related-urls] related-urls->expected-parsed)
        ;; ISO does not have distribution centers as Organization
        (assoc :organizations organizations)
        ;; ISO does not support sensor technique or platform characteristics
        (update-in [:platforms] platforms->expected-parsed)
        ;; ISO does not support size in RelatedURLs
        (update-in [:related-urls] related-urls->expected-parsed)
        ;; ISO does not fully support two-d-coordinate-systems
        (dissoc :two-d-coordinate-systems)
        ;; It looks like ISO-19115-2 does not have a string we can extract representing quality.
        ;; ISO-19115-1 will have a string which we can extract.
        (dissoc :quality)
        (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
        (update :spatial-coverage add-redundant-bounding-boxes)
        (assoc :personnel personnel)
        ;; publication-reference will be added later
        (dissoc :publication-references)
        (dissoc :collection-citations)
        (dissoc :collection-progress)
        umm-c/map->UmmCollection)))

(defn derive-geometries
  "Returns SpatialCoverage with all geometries updated by calling
  calculate-derived with the collection coordinate system."
  [{cs :spatial-representation :as sc}]
  (when sc
    (let [derive #(d/calculate-derived (umm-s/set-coordinate-system cs %))]
      (update-in sc [:geometries] (partial map derive)))))

(defspec generate-collection-is-valid-xml-test 100
  (for-all [collection coll-gen/collections]
    (let [xml (iso/umm->iso-mends-xml collection)]
      (and
        (> (count xml) 0)
        (= 0 (count (c/validate-xml xml)))))))

(deftest generate-and-parse-collection-test
  (checking "collection round tripping" 100
    [collection coll-gen/collections]
    (let [xml (iso/umm->iso-mends-xml collection)
          parsed (c/parse-collection xml)
          expected-parsed (umm->expected-parsed-iso collection)]
      (is (= expected-parsed parsed)))))

(deftest generate-and-parse-collection-between-formats-test
  (checking "parse between formats" 100
    [collection coll-gen/collections]
    (let [xml (iso/umm->iso-mends-xml collection)
          parsed-iso (c/parse-collection xml)
          echo10-xml (echo10/umm->echo10-xml parsed-iso)
          parsed-echo10 (echo10-c/parse-collection echo10-xml)
          ;; fudge the spatial coverage here because ECHO 10 doesn't
          ;; apply the collection spatial representation to the
          ;; geometries it contains...
          parsed-echo10 (update-in parsed-echo10 [:spatial-coverage] spatial-coverage->expected-parsed)
          expected-parsed (test-echo10/umm->expected-parsed-echo10 (umm->expected-parsed-iso collection))]
      (is (= parsed-echo10 expected-parsed))
      (is (= 0 (count (echo10-c/validate-xml echo10-xml)))))))

(comment

  ;; This test is currently failing pending an update to the XSLT file
  ;; to generate closed polygons per the GML spec

  (def echo-to-iso-xslt
    (xslt/read-template
      (io/resource "schema/iso_mends/resources/transforms/ECHOToISO.xsl")))

  (defspec umm-to-echo-to-iso-mends-via-xslt-to-umm-test 100
    (for-all [collection coll-gen/collections]
      (let [echo10-xml (echo10/umm->echo10-xml collection)
            iso-xml    (xslt/transform echo10-xml echo-to-iso-xslt)
            parsed-iso (c/parse-collection iso-xml)]
        ;; only comparing the parsed :spatial-coverage, since there are
        ;; funky parts in the rest of the XSLT output
        (= (:spatial-coverage (umm->expected-parsed-iso collection))
           (:spatial-coverage (umm->expected-parsed-iso parsed-iso)))))))

;; This is a made-up include all fields collection xml sample for the parse collection test
(def all-fields-collection-xml
  (slurp (io/file (io/resource "data/iso_mends/all_fields_iso_collection.xml"))))

(def valid-collection-xml
  (slurp (io/file (io/resource "data/iso_mends/sample_iso_collection.xml"))))

(def real-data-collection-xml
  (slurp (io/file (io/resource "data/iso_mends/C1216109961-NSIDCV0TST.xml"))))

(def expected-temporal
  (umm-c/map->Temporal
    {:range-date-times
     [(umm-c/map->RangeDateTime
        {:beginning-date-time (p/parse-datetime "1996-02-24T22:20:41-05:00")
         :ending-date-time (p/parse-datetime "1997-03-24T22:20:41-05:00")})
      (umm-c/map->RangeDateTime
        {:beginning-date-time (p/parse-datetime "1998-02-24T22:20:41-05:00")
         :ending-date-time (p/parse-datetime "1999-03-24T22:20:41-05:00")})]
     :single-date-times
     [(p/parse-datetime "2010-01-05T05:30:30.550-05:00")]
     :periodic-date-times []}))

(def expected-collection
  (umm-c/map->UmmCollection
    {:entry-title "MINIMAL > A minimal valid collection"
     :summary "A minimal valid collection"
     :purpose "A grand purpose"
     :metadata-language "eng"
     :product (umm-c/map->Product
                {:short-name "MINIMAL"
                 :long-name "A minimal valid collection"
                 :version-id "1"
                 :processing-level-id "1B"})
     :access-value 4.2
     :use-constraints "Restriction Comment:"
     :data-provider-timestamps (umm-c/map->DataProviderTimestamps
                                 {:insert-time (p/parse-datetime "1999-12-30T19:00:00-05:00")
                                  :update-time (p/parse-datetime "1999-12-31T19:00:00-05:00")
                                  :revision-date-time (p/parse-datetime "1999-12-31T19:00:00-05:00")})
     :spatial-keywords ["Word-2" "Word-1" "Word-0"]
     :temporal-keywords ["Word-5" "Word-3" "Word-4"]
     :temporal expected-temporal
     :spatial-coverage (umm-c/map->SpatialCoverage {:granule-spatial-representation :cartesian})
     :science-keywords
     [(umm-c/map->ScienceKeyword
        {:category "EARTH SCIENCE"
         :topic "CRYOSPHERE"
         :term "SNOW/ICE"
         :variable-level-1 "ALBEDO"
         :variable-level-2 "BETA"
         :variable-level-3 "GAMMA"
         :detailed-variable "DETAILED"})
      (umm-c/map->ScienceKeyword
        {:category "EARTH SCIENCE"
         :topic "CRYOSPHERE"
         :term "SEA ICE"
         :variable-level-1 "REFLECTANCE"})]
     :platforms
     [(umm-c/map->Platform
        {:short-name "RADARSAT-1"
         :long-name "RADARSAT-LONG-1"
         :type "Spacecraft"
         :instruments [(umm-c/map->Instrument
                         {:short-name "SAR"
                          :long-name "SAR long name"
                          :sensors [(umm-c/map->Sensor {:short-name "SNA"
                                                        :long-name "SNA long name"})
                                    (umm-c/map->Sensor {:short-name "SNB"})]})
                       (umm-c/map->Instrument {:short-name "MAR"})]})
      (umm-c/map->Platform
        {:short-name "RADARSAT-2"
         :long-name "RADARSAT-LONG-2"
         :type "Spacecraft-2"
         :instruments nil})]
     :product-specific-attributes
     [(umm-c/map->ProductSpecificAttribute
        {:name "SIPSMetGenVersion"
         :description "The version of the SIPSMetGen software used to produce the metadata file for this granule"
         :data-type :string
         :parameter-range-begin "alpha"
         :parameter-range-end "bravo"
         :value "alpha1"
         :parsed-parameter-range-begin "alpha"
         :parsed-parameter-range-end "bravo"
         :parsed-value "alpha1"})
      (umm-c/map->ProductSpecificAttribute
         {:name "No description"
          :description "Not provided"
          :data-type :string
          :value "alpha2"
          :parsed-value "alpha2"})]
     :collection-associations [(umm-c/map->CollectionAssociation
                                 {:short-name "COLLOTHER-237"
                                  :version-id "1"})
                               (umm-c/map->CollectionAssociation
                                 {:short-name "COLLOTHER-238"
                                  :version-id "1"})
                               (umm-c/map->CollectionAssociation
                                 {:short-name "COLLOTHER-239"
                                  :version-id "1"})]
     :projects
     [(umm-c/map->Project
        {:short-name "ESI"
         :long-name "Environmental Sustainability Index"})
      (umm-c/map->Project
        {:short-name "EVI"
         :long-name "Environmental Vulnerability Index"})
      (umm-c/map->Project
        {:short-name "EPI"
         :long-name "Environmental Performance Index"})]
     :related-urls
     [(umm-c/map->RelatedURL
        {:type "GET DATA"
         :url "http://ghrc.nsstc.nasa.gov/hydro/details.pl?ds=dc8capac"})
      (umm-c/map->RelatedURL
        {:type "GET DATA"
         :url "http://camex.nsstc.nasa.gov/camex3/"})
      (umm-c/map->RelatedURL
        {:type "VIEW RELATED INFORMATION"
         :url "http://ghrc.nsstc.nasa.gov/uso/ds_docs/camex3/dc8capac/dc8capac_dataset.html"})
      (umm-c/map->RelatedURL
        {:type "GET RELATED VISUALIZATION"
         :url "ftp://camex.nsstc.nasa.gov/camex3/dc8capac/browse/"
         :description "Some description."
         :title "Some description."})]
     :associated-difs ["DIF-255" "DIF-256" "DIF-257"]
     :organizations
     [(umm-c/map->Organization
        {:type :processing-center
         :org-name "SEDAC PC"})
      (umm-c/map->Organization
        {:type :archive-center
         :org-name "SEDAC AC"})]
     :personnel [(umm-c/map->Personnel
                   {:last-name "SEDAC AC"
                    :roles ["pointOfContact"]})
                 (umm-c/map->Personnel
                   {:last-name "John Smith"
                    :roles ["pointOfContact"]})
                 (umm-c/map->Personnel
                   {:last-name "SEDAC AC"
                    :roles ["distributor"]})]}))

(deftest parse-collection-test
  (testing "parse collection"
    (is (= expected-collection (c/parse-collection all-fields-collection-xml))))
  (testing "parse temporal"
    (is (= expected-temporal (c/parse-temporal all-fields-collection-xml))))
  (testing "parse access value"
    (is (= 4.2 (c/parse-access-value all-fields-collection-xml)))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (c/validate-xml valid-collection-xml)))))
  (testing "invalid xml"
    (is (= [(str "Exception while parsing invalid XML: Line 15 - cvc-complex-type.2.4.a: Invalid content was found "
                 "starting with element 'gmd:XXXX'. One of "
                 "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":language, "
                 "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                 "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                 "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]
           (c/validate-xml (s/replace valid-collection-xml "fileIdentifier" "XXXX"))))))

(deftest parse-collection-defaults-test
  ;; Check that defaults are being added correctly to create valid umm
  (let [umm (c/parse-collection real-data-collection-xml)]
    (testing "default granule spatial represetation"
      (is (= :no-spatial (get-in umm [:spatial-coverage :granule-spatial-representation]))))
    (testing "default ScienceKeywords Term"
      (is (= umm-c/not-provided (->> umm :science-keywords first :term))))))
