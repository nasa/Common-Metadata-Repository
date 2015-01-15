(ns ^{:doc "CMR Ingest validation integration tests"}
  cmr.system-int-test.ingest.validation-test
  (:require [clojure.test :refer :all]
            [cmr.system-int-test.utils.ingest-util :as ingest]
            [cmr.system-int-test.data2.collection :as dc]
            [cmr.system-int-test.data2.granule :as dg]
            [cmr.system-int-test.data2.core :as d]
            [cmr.system-int-test.ingest.collection-ingest-test :as cit]
            [cmr.system-int-test.ingest.granule-ingest-test :as git]))

(use-fixtures :each (ingest/reset-fixture {"provguid1" "PROV1"}))

(deftest valid-collection-validation-test
  (testing "successful validation of collection"
    (let [concept (cit/collection-for-ingest {})
          {:keys [status errors]} (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors])))))

(deftest invalid-collection-xml-test
  (testing "invalid collection xml fails validation with appropriate message"
    (let [concept (cit/collection-for-ingest {})
          {:keys [status errors]}
          (ingest/validate-concept (assoc concept :metadata "<Collection>invalid xml</Collection>"))]
      (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Collection' cannot have character [children], because the type's content type is element-only."
                   "Line 1 - cvc-complex-type.2.4.b: The content of element 'Collection' is not complete. One of '{ShortName}' is expected."]]
             [status errors])))))

(deftest valid-granule-validation-test
  (testing "successful validation of granule"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          concept (git/umm-granule->granule-concept (dg/granule collection))
          {:keys [status errors]}
          (ingest/validate-concept concept)]
      (is (= [200 nil] [status errors])))))

(deftest invalid-granule-xml-test
  (testing "invalid granule xml fails validation with appropriate message"
    (let [collection (d/ingest "PROV1" (dc/collection {}))
          concept (git/umm-granule->granule-concept (dg/granule collection))
          {:keys [status errors]} (ingest/validate-concept(assoc concept :metadata "<Granule>invalid xml</Granule>"))]
      (is (= [400 ["Line 1 - cvc-complex-type.2.3: Element 'Granule' cannot have character [children], because the type's content type is element-only."
                   "Line 1 - cvc-complex-type.2.4.b: The content of element 'Granule' is not complete. One of '{GranuleUR}' is expected."]]
             [status errors])))))