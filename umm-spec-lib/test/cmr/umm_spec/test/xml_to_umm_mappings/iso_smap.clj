(ns cmr.umm-spec.test.xml-to-umm-mappings.iso-smap
  "Tests the ISO SMAP check and convertion of Granule Spatial Representation in a Collection. Its the
   same code also for ISO MENDS."
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.test :refer :all]
   [cmr.common.util :as common-util :refer [are3]]
   [cmr.common.xml.simple-xpath :refer [select]]
   [cmr.umm-spec.xml-to-umm-mappings.iso-smap.spatial :as spatial]))

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
