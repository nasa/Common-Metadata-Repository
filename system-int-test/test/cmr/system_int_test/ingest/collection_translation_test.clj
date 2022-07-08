(ns cmr.system-int-test.ingest.collection-translation-test
  (:require
    [clojure.java.io :as io]
    [clojure.string :as string]
    [clojure.test :refer :all]
    [cmr.common.mime-types :as mime-types]
    [cmr.common.util :as util :refer [update-in-each]]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.umm-spec.models.umm-collection-models :as umm-c]
    [cmr.umm-spec.models.umm-common-models :as umm-cmn]
    [cmr.umm-spec.test.expected-conversion :as expected-conversion]
    [cmr.umm-spec.test.location-keywords-helper :as location-keywords-helper]
    [cmr.umm-spec.umm-spec-core :as umm-spec]))

(def valid-formats
  [:umm-json
   :iso19115
   :iso-smap
   :dif
   :dif10
   :echo10])

(def test-context (location-keywords-helper/setup-context-for-test))

(defn assert-translate-failure
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 400 status))
    (is (re-find error-regex body))))

(defn assert-invalid-data
  [error-regex & args]
  (let [{:keys [status body]} (apply ingest/translate-metadata args)]
    (is (= 422 status))
    (is (re-find error-regex body))))

(defn- convert-to-sets
  "Convert lists in the umm record to sets so order doesn't matter during comparison"
  [record]
  (-> record
      (update :ContactPersons set)
      (update :RelatedUrls set)))

(defn- remove-all-nil-keys-from-hdr
  "Remove all the nil keys inside HorizontalDataResolution."
  [hdr]
  (-> hdr
      (update-in-each [:NonGriddedResolutions] util/remove-nil-keys)
      (update-in-each [:NonGriddedRangeResolutions] util/remove-nil-keys)
      (update-in-each [:GeneticResolutions] util/remove-nil-keys)
      (update-in-each [:GriddedResolutions] util/remove-nil-keys)
      (update-in-each [:GriddedRangeResolutions] util/remove-nil-keys)
      (util/remove-nil-keys)))

(deftest translate-metadata
  (doseq [input-format valid-formats
          output-format valid-formats]
    (testing (format "Translating %s to %s" (name input-format) (name output-format))
      (let [input-str (umm-spec/generate-metadata test-context expected-conversion/example-collection-record input-format)
            expected (expected-conversion/convert expected-conversion/example-collection-record input-format output-format)
            expected (update-in-each expected [:Platforms] update-in-each [:Instruments] assoc
                                               :NumberOfInstruments nil)
            expected (update-in
                       expected
                       [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]
                       remove-all-nil-keys-from-hdr)
            expected (convert-to-sets expected)
            {:keys [status headers body]} (ingest/translate-metadata :collection input-format input-str output-format)
            content-type (first (mime-types/extract-mime-types (:content-type headers)))
            parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)
            parsed-umm-json (update-in-each parsed-umm-json [:Platforms] update-in-each [:Instruments] assoc
                                                             :NumberOfInstruments nil)
            parsed-umm-json (update-in
                              parsed-umm-json
                              [:SpatialExtent :HorizontalSpatialDomain :ResolutionAndCoordinateSystem :HorizontalDataResolution]
                              remove-all-nil-keys-from-hdr)
            parsed-umm-json (convert-to-sets parsed-umm-json)
            expected (util/remove-nil-keys expected)
            parsed-umm-json (util/remove-nil-keys expected)]

        (is (= 200 status) body)
        (is (= (mime-types/format->mime-type output-format) content-type))
        ;; when translating from dif9 to echo10,
        ;; The expected is umm-C->dif9->umm-C->echo10->umm-C
        ;; The DataDates part is lost at dif9->umm-C. so in the end there is no DataDates in the expected.
        ;; The parsed-umm-json is from dif9 converted umm-C->echo10->umm-C, so there is no DataDates to start.
        ;; Current-time is used from umm-C to echo10, then when convert back from echo10 to umm-C,
        ;; the current-time can not be removed like before when the default was used because it is not a constant.
        ;; We can't modify the expected because we can't expect a changing current-time either.
        (if (and (= "dif" (name input-format)) (= "echo10" (name output-format)))
          (is (= expected (dissoc parsed-umm-json :DataDates)))
          (is (= expected parsed-umm-json))))))

  (testing (format "Translating iso19115 to umm-json produces the right UseConstraints")
    (let [input-format :iso19115
          output-format :umm-json
          options {:skip-sanitize-umm-c false}
          input-xml (slurp (io/resource "CMR-4839-4651/iso19115_artificial_test_data.xml"))
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                       output-format options)
          parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)]
      (is (= (umm-c/map->UseConstraintsType
               {:Description "First Description"
                :LicenseURL (umm-cmn/map->OnlineResourceType
                              {:Linkage " https://www.nasa.examplelicenseurl1.gov"})})
             (:UseConstraints parsed-umm-json)))
      (is (= 200 status))))

  (testing (format "Translating iso19115 to umm-json produces the right DataCenters")
    (let [input-format :iso19115
          output-format :umm-json
          options {:skip-sanitize-umm-c false}
          input-xml (slurp (io/resource "CMR-7636/iso19115_test_data.xml"))
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                       output-format options)
          parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)]
      (is (= [(umm-cmn/map->DataCenterType
               {:Roles ["ARCHIVER"] 
                :ShortName "Not provided"})]
             (:DataCenters parsed-umm-json)))
      (is (= 200 status))))

  (testing (format "Translating umm-json to iso19115 produces the right codelist")
    (let [input-format :umm-json
          output-format :iso19115
          options {:skip-sanitize-umm-c false}
          input-json (slurp (io/resource "CMR-7557/test_data.json"))
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-json
                                                                       output-format options)]
      (is (string/includes? body "codeList=\"http://data.noaa.gov/"))
      (is (not (string/includes? body "codeList=\"http://www.ngdc.noaa.gov/")))
      (is (= 200 status))))

  (testing (format "Translating iso19115 to umm-json without skipping sanitizing makes use of default values")
    (let [input-format :iso19115
          output-format :umm-json
          options {:skip-sanitize-umm-c false}
          collection (assoc expected-conversion/example-collection-record :DataCenters nil)
          input-xml (umm-spec/generate-metadata test-context collection input-format)
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                       output-format options)
          parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)]
      (is (= [(umm-cmn/map->DataCenterType {:Roles ["ARCHIVER"], :ShortName "Not provided"})]
             (:DataCenters parsed-umm-json)))
      (is (= 200 status))))

  (testing (format "Translating iso19115 to umm-json with skipping sanitizing and without skipping umm validation")
    (let [input-format :iso19115
          output-format :umm-json
          options {:skip-sanitize-umm-c true}
          collection (assoc expected-conversion/example-collection-record :DataCenters nil)
          input-xml (umm-spec/generate-metadata test-context collection input-format)
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                          output-format options)]
      (is (= "{\"errors\":[\"#: required key [DataCenters] not found\"]}" body))
      (is (= 422 status))))

  (testing (format "Translating iso19115 to umm-json with skipping sanitizing and with skipping validation")
    (let [input-format :iso19115
          output-format :umm-json
          options {:skip-sanitize-umm-c true :query-params {:skip_umm_validation true}}
          collection (assoc expected-conversion/example-collection-record :DataCenters nil)
          input-xml (umm-spec/generate-metadata test-context collection input-format)
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                       output-format options)
          parsed-umm-json (umm-spec/parse-metadata test-context :collection output-format body)]
      (is (= nil (:DataCenters parsed-umm-json)))
      (is (= 200 status))))

  (testing (format "Translating iso19115 to dif10 with skipping sanitizing")
    (let [input-format :iso19115
          output-format :dif10
          options {:skip-sanitize-umm-c true}
          input-xml (umm-spec/generate-metadata test-context expected-conversion/example-collection-record input-format)
          {:keys [status body]} (ingest/translate-metadata :collection input-format input-xml
                                                                       output-format options)]
      (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>Skipping santization during translation is only supported when the target format is UMM-C</error></errors>" body))
      (is (= 400 status))))

  (testing "Failure cases"
    (testing "unsupported input format"
      (assert-translate-failure
        #"The mime types specified in the content-type header \[application/xml\] are not supported"
        :collection :xml "notread" :umm-json))

    (testing "not specified input format"
      (assert-translate-failure
        #"The mime types specified in the content-type header \[\] are not supported"
        :collection nil "notread" :umm-json))

    (testing "unsupported output format"
      (assert-translate-failure
        #"The mime types specified in the accept header \[application/xml\] are not supported"
        :collection :echo10 "notread" :xml))

    (testing "not specified output format"
      (assert-translate-failure
        #"The mime types specified in the accept header \[\] are not supported"
        :collection :echo10 "notread" nil))

    (testing "invalid metadata"
      (testing "bad xml"
        (assert-translate-failure
          #"Cannot find the declaration of element 'this'"
          :collection :echo10 "<this> is not good XML</this>" :umm-json))

      (testing "wrong xml format"
        (assert-translate-failure
          #"Element 'Entry_ID' is a simple type, so it must have no element information item"
          :collection :dif (umm-spec/generate-metadata test-context expected-conversion/example-collection-record :dif10) :umm-json))

      (testing "bad json"
        (assert-translate-failure #"#: required key \[.*\] not found"
                                  :collection :umm-json "{}" :echo10)))))

(deftest translate-metadata-handles-date-string
  (testing "CMR-2257: date-string in DIF9 causes InternalServerError"
    (let [dif9-xml "<DIF xmlns=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:dif=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/ http://gcmd.gsfc.nasa.gov/Aboutus/xml/dif/dif_v9.8.4.xsd\">
                   <Entry_ID>minimal_dif_dataset</Entry_ID>
                   <Entry_Title>A minimal dif dataset</Entry_Title>
                   <Data_Set_Citation>
                   <Dataset_Title>dataset_title</Dataset_Title>
                   </Data_Set_Citation>
                   <Parameters>
                   <Category>category</Category>
                   <Topic>topic</Topic>
                   <Term>term</Term>
                   </Parameters>
                   <Temporal_Coverage>
                   <Start_Date>1975-01-01</Start_Date>
                   </Temporal_Coverage>
                   <Data_Center>
                   <Data_Center_Name>
                   <Short_Name>datacenter_short_name</Short_Name>
                   <Long_Name>data center long name</Long_Name>
                   </Data_Center_Name>
                   <Personnel>
                   <Role>DummyRole</Role>
                   <Last_Name>UNEP</Last_Name>
                   </Personnel>
                   </Data_Center>
                   <Summary>
                   <Abstract>summary of the dataset</Abstract>
                   <Purpose>A grand purpose</Purpose>
                   </Summary>
                   <Metadata_Name>CEOS IDN DIF</Metadata_Name>
                   <Metadata_Version>VERSION 9.8.4</Metadata_Version>
                   <Last_DIF_Revision_Date>2013-10-22</Last_DIF_Revision_Date>
                   </DIF>"
                   {:keys [status]} (ingest/translate-metadata
                                           :collection :dif dif9-xml :umm-json
                                           {:query-params {"skip_umm_validation" "true"}})]
      (is (= 200 status)))))
