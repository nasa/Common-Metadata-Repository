(ns cmr.system-int-test.ingest.collection-validation-end-point-test
  "CMR Ingest validation end point integration tests"
  (:require
    [clojure.test :refer :all]
    [cmr.system-int-test.utils.ingest-util :as ingest]
    [cmr.system-int-test.data2.collection :as dc]
    [cmr.system-int-test.data2.core :as d]
    [cmr.umm-spec.test.expected-conversion :as exp-conv]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; The following tests are included in this file
;; See individual deftest for detailed test info.
;; 1. Validation end point test 
;; 2. successful-validation-with-accept-header-test 
;; 3. failed-validation-without-headers-returns-xml 
;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1" "provguid2" "PROV2"}))

(deftest validation-endpoint-test
  (testing "successful validation of collection"
    (let [concept (dc/collection-concept {})
          {:keys [status errors]} (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors]))))
 
 (testing "invalid collection xml fails validation with appropriate message"
    (let [concept (dc/collection-concept {})
          {:keys [status errors]}
          (ingest/validate-concept (assoc concept
                                          :metadata "<Collection>invalid xml</Collection>"))]
      (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only."
                   "Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected."]]
             [status errors])))))

(deftest successful-validation-with-accept-header-test
  (testing "successful validation requests do not get an xml or json response body"
    (are [accept]
         (let [collection (dc/collection-dif10 {:processing-level-id "Level 1"})
               concept (dc/collection-concept collection :dif10)
               response-map (select-keys (ingest/validate-concept concept {:accept-format accept :raw? true})
                                         [:status :body])]
           (= {:status 200 :body ""} response-map))
         :json :xml)))

(deftest failed-validation-without-headers-returns-xml
  (testing "failed validations with no accept or content-type header return xml"
    (let [concept (assoc (dc/collection-concept {}) :metadata "<Collection>invalid xml</Collection>")
          {:keys [status body]} (ingest/validate-concept concept {:raw? true})]
      (is (= [400 "<?xml version=\"1.0\" encoding=\"UTF-8\"?><errors><error>Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only.</error><error>Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected.</error></errors>"]
             [status body])))))

