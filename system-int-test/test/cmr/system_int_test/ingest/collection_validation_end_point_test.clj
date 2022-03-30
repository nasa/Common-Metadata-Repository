(ns cmr.system-int-test.ingest.collection-validation-end-point-test
  "CMR Ingest validation end point integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.umm-spec-collection :as data-umm-c]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest validation-endpoint-test
  (testing "successful validation of collection"
    (let [concept (data-umm-c/collection-concept {})
          {:keys [status errors]} (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors]))))

 (testing "invalid collection xml fails validation with appropriate message"
    (let [concept (data-umm-c/collection-concept {})
          {:keys [status errors]}
          (ingest/validate-concept (assoc concept
                                          :metadata "<Collection>invalid xml</Collection>"))]
      (is (= [400 ["Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only."
                   "Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected."]]
             [status errors])))))

(deftest successful-validation-with-accept-header-test
  (testing "successful validation requests do not get an xml or json response body"
    (are [accept warnings]
         (let [collection (data-umm-c/collection {})
               concept (data-umm-c/collection-concept collection :dif10)
               response-map (select-keys (ingest/validate-concept concept {:accept-format accept :raw? true})
                                         [:status :body])]
           (= {:status 200 :body warnings} response-map))
         :json
         "{\"warnings\":[\"After translating item to UMM-C the metadata had the following issue(s): [:Platforms 0] Platform short name [A340-600], long name [Airbus A340-600], and type [null] was not a valid keyword combination.;; [:Platforms 0 :Instruments 0] Instrument short name [Not provided] and long name [null] was not a valid keyword combination.;; [:Projects 0] Project short name [Not provided] and long name [null] was not a valid keyword combination.\"]}"

         :xml
         "<?xml version=\"1.0\" encoding=\"UTF-8\"?><result><warnings>After translating item to UMM-C the metadata had the following issue(s): [:Platforms 0] Platform short name [A340-600], long name [Airbus A340-600], and type [null] was not a valid keyword combination.;; [:Platforms 0 :Instruments 0] Instrument short name [Not provided] and long name [null] was not a valid keyword combination.;; [:Projects 0] Project short name [Not provided] and long name [null] was not a valid keyword combination.</warnings></result>")))

(deftest failed-validation-without-headers-returns-xml
  (testing "failed validations with no accept or content-type header return xml"
    (let [concept (assoc (data-umm-c/collection-concept {}) :metadata "<Collection>invalid xml</Collection>")
          {:keys [status body]} (ingest/validate-concept concept {:raw? true})]
      (is (= [400 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only.</error><error>Exception while parsing invalid XML: Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected.</error></errors>"]
             [status body])))))
