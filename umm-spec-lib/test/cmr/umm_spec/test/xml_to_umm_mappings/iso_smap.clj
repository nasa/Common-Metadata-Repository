(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-smap
  "Tests the ISO SMAP check and convertion of Granule Spatial Representation in a Collection. Its the
   same code also for ISO MENDS."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as common-util :refer [are3]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.models.umm-collection-models :as umm-c]
   [cmr.umm-spec.umm-spec-core :as core]
   [cmr.umm-spec.umm-to-xml-mappings.iso-smap :as iso]
   [cmr.umm-spec.util :as u]
   [cmr.umm-spec.xml-to-umm-mappings.iso-shared.doi :as doi]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap :as parser]))

(def md-identification-base-xpath
  (str "/gmd:DS_Series/gmd:seriesMetadata/gmi:MI_Metadata"
       "/gmd:identificationInfo/gmd:MD_DataIdentification"))

(deftest iso-smap-granule-spatial-representation-test
  "This tests the iso smap granule spatial representation mapping."

  (let [spatial-extent-xpath (str md-identification-base-xpath
                                  "/gmd:extent/gmd:EX_Extent")
        base-record (slurp (io/resource "example-data/iso-smap/artificial_test_data_2.xml"))
        actual-cartesian-record (string/replace base-record
                                                #"(?s)Barring any anomalies during the SMAP.*island shoreline."
                                                "SpatialCoverageType=HORIZONTAL,SpatialGranuleSpatialRepresentation=CARTESIAN")
        actual-alt-cartesian-record (slurp (io/resource "example-data/iso-smap/artificial_test_data_2_alt_granule_rep.xml"))]

    (are3 [expected-result record]
      (let [data-id-el (first (select record md-identification-base-xpath))]
        (is (= expected-result
               (:GranuleSpatialRepresentation (spatial/parse-spatial record data-id-el spatial-extent-xpath {:sanitize? false})))))

      "Test Normal case with no SpatialGranuleSpatialRepresentation in description field."
      "GEODETIC"
      base-record

      "Test with SpatialGranuleSpatialRepresentation in description field."
      "CARTESIAN"
      actual-cartesian-record

      "Test with SpatialGranuleSpatialRepresentation in alternate location."
      "CARTESIAN"
      actual-alt-cartesian-record)))

(deftest associated-doi-test
  "Testing the associated DOIs"

  (are3 [iso-record expect-empty]
    (let [parsed (parser/iso-smap-xml-to-umm-c iso-record u/default-parsing-options)
          ;; use the parsed associated DOIs as the expected value
          expected-associated-dois (:AssociatedDOIs parsed)
          generated-iso (iso/umm-c-to-iso-smap-xml parsed)
          ;; parse out the associated DOIs
          parsed-associated-dois
           (when-let [dois (seq
                             (for [doi (doi/parse-associated-dois generated-iso
                                                                  parser/associated-doi-xpath)]
                               (umm-c/map->AssociatedDoiType doi)))]
             (into [] dois))]

      ; validate against xml schema
      (is (empty? (core/validate-xml :collection :iso-smap generated-iso)))
      (if expect-empty
        (is (empty? parsed-associated-dois))
        (is (not (empty? parsed-associated-dois))))
      (is (= expected-associated-dois parsed-associated-dois)))

    "Associated DOIs are written out correctly."
    (slurp (io/resource "example-data/iso-smap/artificial_test_data_2.xml"))
    false

    "Associated DOIs not used"
    (slurp (io/resource "example-data/iso-smap/artificial_test_data_3.xml"))
    true))
