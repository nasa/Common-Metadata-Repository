(ns cmr.umm.test.echo10.related-url
  "Tests functions for granule related urls"
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.umm.echo10.related-url :as rurl]))

(deftest validate-xml
  (testing
    "Validate that the related-url->online-resource function correctly returns
    subtypes for the DMR use cases"
    (are3 [expected actual-type actual-subtype]
          (is (= expected
                 (#'rurl/related-url->online-resource {:type actual-type
                                                       :sub-type actual-subtype})))
          "Extended metadata with DMR++"
          "EXTENDED METADATA : DMR++"
          "EXTENDED METADATA"
          "DMR++"

          "Extended metadata with DMR++ Missing Data"
          "EXTENDED METADATA : DMR++ MISSING DATA"
          "EXTENDED METADATA"
          "DMR++ MISSING DATA"

          "A future case of extended metadata which should not be changed"
          "USER SUPPORT"
          "EXTENDED METADATA"
          "Future Kathryn Janeway"

          "User support case for existing types"
          "USER SUPPORT"
          "DOWNLOAD SOFTWARE"
          "APP"

          "User support should be assumed for a nil"
          "USER SUPPORT"
          "type"
          nil

          "User support should be assumed for a nil"
          "USER SUPPORT"
          nil
          "sub"

          "User support should be assumed for a nil"
          "USER SUPPORT"
          nil
          nil)))
