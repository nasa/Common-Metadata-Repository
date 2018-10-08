(ns cmr.umm.test.iso-smap.granule
  "Tests parsing and generating SMAP ISO Granule XML."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :refer [for-all]]
   [cmr.common.date-time-parser :as p]
   ;; Temporarily included to use the fixed defspec. Remove once issue is fixed.
   [cmr.common.test.test-check-ext :refer [defspec]]
   [cmr.common.util :as util]
   [cmr.spatial.mbr :as mbr]
   [cmr.spatial.polygon :as poly]
   [cmr.umm.iso-smap.granule :as g]
   [cmr.umm.iso-smap.iso-smap-core :as iso]
   [cmr.umm.test.generators.granule :as gran-gen]
   [cmr.umm.umm-collection :as umm-c]
   [cmr.umm.umm-granule :as umm-g]
   [cmr.umm.umm-spatial :as spatial]))

(defn- data-granule->expected-parsed
  "Returns the expected parsed data-granule for the given data-granule"
  [data-granule]
  (when data-granule
    (-> data-granule
        (assoc :day-night nil)
        (assoc :size nil))))

(defn set-geodetic
  [obj]
  (spatial/set-coordinate-system :geodetic obj))

(defn- spatial-coverage->expected-parsed
  "Returns the expected parsed spatial-coverage for the given spatial-coverage"
  [{:keys [geometries]}]
  (when (seq geometries)
    (umm-g/map->SpatialCoverage {:geometries (map set-geodetic geometries)})))

(defn- related-url->expected-parsed
  [related-url]
  (-> related-url
      (assoc :title nil)
      (assoc :description nil)
      (assoc :size nil)))

(defn- related-urls->expected-parsed
  "Returns the expected parsed related-urls for the given related-urls."
  [related-urls]
  (seq (map related-url->expected-parsed related-urls)))

(defn- umm->expected-parsed-smap-iso
  "Modifies the UMM record for testing SMAP ISO. ISO contains a subset of the total UMM fields
  so certain fields are removed for comparison of the parsed record"
  [gran]
  (-> gran
      ;; SMAP ISO collection-ref does not have entry-id
      (assoc-in [:collection-ref :entry-id] nil)
      ;; There is no delete-time in SMAP ISO
      (assoc-in [:data-provider-timestamps :delete-time] nil)
      ;; SMAP ISO data-granule does not have day-night or size
      (update-in [:data-granule] data-granule->expected-parsed)
      ;; SMAP ISO spatial only has BoundingBox
      (update-in [:spatial-coverage] spatial-coverage->expected-parsed)
      ;; SMAP ISO related-urls does not have title, description or size
      (update-in [:related-urls] related-urls->expected-parsed)
      ;; SMAP ISO does not support platform-refs
      (dissoc :platform-refs)
      ;; SMAP ISO does not support project-refs
      (dissoc :project-refs)
      ;; SMAP ISO does not support product-specific-attributes
      (dissoc :product-specific-attributes)
      ;; SMAP ISO does not support cloud-cover
      (dissoc :cloud-cover)
      ;; SMAP ISO does not support two-d-coordinate-system
      (dissoc :two-d-coordinate-system)
      ;; SMAP ISO does not support measured-parameters
      (dissoc :measured-parameters)
      ;; trim :orbital-model-name in each :orbit-calculated-spatial-domains
      (util/update-in-each [:orbit-calculated-spatial-domains]
                           #(assoc % :orbital-model-name (string/trim (:orbital-model-name %))))
      umm-g/map->UmmGranule))

(defspec generate-granule-is-valid-xml-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (iso/umm->iso-smap-xml granule)]
      (and
        (> (count xml) 0)
        (= 0 (count (g/validate-xml xml)))))))

(defspec generate-and-parse-granule-test 100
  (for-all [granule gran-gen/granules]
    (let [xml (iso/umm->iso-smap-xml granule)
          parsed (g/parse-granule xml)
          expected-parsed (umm->expected-parsed-smap-iso granule)]
      (= parsed expected-parsed))))

(def sample-granule-xml
  (slurp (io/file (io/resource "data/iso_smap/sample_smap_iso_granule.xml"))))

(def sample-granule-xml-orbit
  (slurp (io/file (io/resource "data/iso_smap/CMR-5129.xml"))))

(def multiple-extents-granule-xml
  (slurp (io/file (io/resource "data/iso_smap/granule_with_multiple_extents.xml"))))

(def expected-temporal
  (umm-g/map->GranuleTemporal
    {:range-date-time
     (umm-c/map->RangeDateTime
       {:beginning-date-time (p/parse-datetime "2015-05-30T16:01:00.000Z")
        :ending-date-time (p/parse-datetime "2015-05-30T16:01:06.003Z")})}))

(def expected-orbit
  (umm-g/map->SpatialCoverage
   {:orbit
    (umm-g/->Orbit -140.0 1.0 :desc 0.0 :asc)}))

(def generated-granule1
  (umm-g/map->UmmGranule
    {:granule-ur "!", :data-provider-timestamps #cmr.umm.umm_granule.DataProviderTimestamps{:insert-time #=(cmr.common.joda-time/date-time 0 "UTC"), :update-time #=(cmr.common.joda-time/date-time 0 "UTC"), :delete-time nil}, :collection-ref #cmr.umm.umm_granule.CollectionRef{:entry-title "0", :short-name nil, :version-id nil, :entry-id nil}, :data-granule nil, :access-value nil, :temporal #cmr.umm.umm_granule.GranuleTemporal{:range-date-time #cmr.umm.umm_collection.RangeDateTime{:beginning-date-time #=(cmr.common.joda-time/date-time 0 "UTC"), :ending-date-time nil}, :single-date-time nil}, :spatial-coverage nil, :orbit-calculated-spatial-domains [#cmr.umm.umm_granule.OrbitCalculatedSpatialDomain{:orbital-model-name " !", :orbit-number 0, :start-orbit-number 0, :stop-orbit-number 0, :equator-crossing-longitude 1.0, :equator-crossing-date-time #=(cmr.common.joda-time/date-time 0 "UTC")}], :measured-parameters nil, :platform-refs nil, :project-refs nil, :related-urls nil, :product-specific-attributes nil, :cloud-cover nil, :two-d-coordinate-system nil}))

(def generated-granule
  (umm-g/map->UmmGranule
   {:granule-ur "!", :data-provider-timestamps #cmr.umm.umm_granule.DataProviderTimestamps{:insert-time #=(cmr.common.joda-time/date-time 0 "UTC"), :update-time #=(cmr.common.joda-time/date-time 0 "UTC"), :delete-time nil}, :collection-ref #cmr.umm.umm_granule.CollectionRef{:entry-title "0", :short-name nil, :version-id nil, :entry-id nil}, :data-granule nil, :access-value nil, :temporal #cmr.umm.umm_granule.GranuleTemporal{:range-date-time #cmr.umm.umm_collection.RangeDateTime{:beginning-date-time #=(cmr.common.joda-time/date-time 0 "UTC"), :ending-date-time nil}, :single-date-time nil}, :spatial-coverage nil, :orbit-calculated-spatial-domains [#cmr.umm.umm_granule.OrbitCalculatedSpatialDomain{:orbital-model-name ": !", :orbit-number 0, :start-orbit-number 0, :stop-orbit-number 0, :equator-crossing-longitude -1.0, :equator-crossing-date-time #=(cmr.common.joda-time/date-time 0 "UTC")}], :measured-parameters nil, :platform-refs nil, :project-refs nil, :related-urls nil, :product-specific-attributes nil, :cloud-cover nil, :two-d-coordinate-system nil}))

(def expected-granule
  (umm-g/map->UmmGranule
    {:granule-ur "SC:SPL1AA.001:12345"
     :data-provider-timestamps (umm-g/map->DataProviderTimestamps
                                 {:insert-time (p/parse-datetime "2013-04-04T15:15:00Z")
                                  :update-time (p/parse-datetime "2013-04-05T17:15:00Z")})
     :collection-ref (umm-g/map->CollectionRef
                       {:entry-title "SMAP Collection Dataset ID"
                        :short-name "SPL1AA"
                        :version-id "002"})
     :data-granule (umm-g/map->DataGranule
                     {:producer-gran-id "SMAP_L1C_S0_HIRES_00016_A_20150530T160100_R03001_001.h5"
                      :production-date-time (p/parse-datetime "2012-12-25T10:36:10.000Z")})
     :access-value 32.0
     :temporal expected-temporal
     :spatial-coverage
     (umm-g/map->SpatialCoverage
       {:geometries
        [(mbr/mbr 0.4701165 0.322525 0.4704968 0.3221629)
         (set-geodetic (poly/polygon [(spatial/ords->ring 0 0, 0 4, 5 6, 5 2, 0 0)]))]})
     :orbit-calculated-spatial-domains
     [(umm-g/map->OrbitCalculatedSpatialDomain
        {:orbit-number 2855,
         :equator-crossing-longitude -140.0,
         :equator-crossing-date-time (p/parse-datetime "2015-10-27T04:43:16.365Z")})
      (umm-g/map->OrbitCalculatedSpatialDomain
        {:orbital-model-name "\"testing\"",
         :orbit-number 2855,
         :equator-crossing-longitude -140.0,
         :equator-crossing-date-time (p/parse-datetime "2015-10-27T04:43:16.365Z")})]
     :related-urls [(umm-c/map->RelatedURL
                      {:type "GET DATA"
                       :url "http://example.com/test1.hdf"
                       :mime-type "application/x-hdf"})
                    (umm-c/map->RelatedURL
                      {:type "VIEW RELATED INFORMATION"
                       :mime-type "/text/xml"
                       :url "http://example.com/test2.xml"})
                    (umm-c/map->RelatedURL
                      {:type "GET RELATED VISUALIZATION"
                       :url "http://example.com/test3.jpg"
                       :mime-type "image/jpeg"})]}))

(deftest parse-granule-test
  (testing "parse granule"
    ;; This tested the related fields, including orbit-calculated-spatial-domain
    ;; is correctly translated from xml to umm.
    (is (= expected-granule (g/parse-granule sample-granule-xml))))
  (testing "round trip for granule with spatial-coverage being nil "
    ;; umm-granule to iso-smap-xml, then back to umm-granule.
    ;; The orbit-calculated-spatial-domain should remain the same.
    (let [generated-granule
           (util/update-in-each generated-granule [:orbit-calculated-spatial-domains]
                                #(assoc % :orbital-model-name (string/trim (:orbital-model-name %))))
          xml (iso/umm->iso-smap-xml generated-granule)
          parsed (g/parse-granule xml)]
      (is (= (:orbit-calculated-spatial-domains generated-granule)
             (:orbit-calculated-spatial-domains parsed)))
      (is (= (:spatial-coverage generated-granule)
             (:spatial-coverage parsed)))))
  (testing "round trip for granule with orbit-calculated-spatial-domains being nil "
    ;; umm-granule to iso-smap-xml, then back to umm-granule.
    ;; The orbit-calculated-spatial-domain should remain the same.
    (let [generated-granule (assoc generated-granule :orbit-calculated-spatial-domains nil)
          xml (iso/umm->iso-smap-xml generated-granule)
          parsed (g/parse-granule xml)]
      (is (= (:orbit-calculated-spatial-domains generated-granule)
             (:orbit-calculated-spatial-domains parsed)))
      (is (= (:spatial-coverage generated-granule)
             (:spatial-coverage parsed)))))
  (testing "round trip for granule with both spatial-coverage and orbit-calculated-spatial-domains "
    ;; umm-granule to iso-smap-xml, then back to umm-granule.
    ;; The orbit-calculated-spatial-domain should remain the same.
    (let [xml (iso/umm->iso-smap-xml expected-granule)
          parsed (g/parse-granule xml)]
      (is (= (:orbit-calculated-spatial-domains expected-granule)
             (:orbit-calculated-spatial-domains parsed)))
      (is (= (:spatial-coverage expected-granule)
             (:spatial-coverage parsed)))))
  (testing "parse temporal"
    (is (= expected-temporal (g/parse-temporal sample-granule-xml))))
  (testing "parse multiple extents, temporal"
    (is (= expected-temporal (g/parse-temporal multiple-extents-granule-xml))))
  (testing "parse access value"
    (is (= 32.0 (g/parse-access-value sample-granule-xml))))
  (testing "parse orbit"
    (is (= expected-orbit (g/parse-spatial sample-granule-xml-orbit)))))

(deftest validate-xml
  (testing "valid xml"
    (is (= 0 (count (g/validate-xml sample-granule-xml)))))
  (testing "invalid xml"
    (is (= [(str "Line 7 - cvc-complex-type.2.4.a: Invalid content was found "
                 "starting with element 'gmd:XXXX'. One of "
                 "'{\"http://www.isotc211.org/2005/gmd\":fileIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":language, "
                 "\"http://www.isotc211.org/2005/gmd\":characterSet, "
                 "\"http://www.isotc211.org/2005/gmd\":parentIdentifier, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevel, "
                 "\"http://www.isotc211.org/2005/gmd\":hierarchyLevelName, "
                 "\"http://www.isotc211.org/2005/gmd\":contact}' is expected.")]
           (g/validate-xml (string/replace sample-granule-xml "fileIdentifier" "XXXX"))))))
