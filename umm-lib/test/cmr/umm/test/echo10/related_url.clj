(ns cmr.umm.test.echo10.related-url
  "Tests functions for granule related urls"
  (:require [clojure.test :refer :all]
            [cmr.umm.echo10.related-url :as rurl]))

(defn- call-related-url-convert
  "Call the private function that will build related url types"
  [related-url-type related-url-sub-type]
  (#'rurl/related-url->online-resource {:type related-url-type
                                        :sub-type related-url-sub-type}))

(deftest validate-xml
  (testing
    "Validate that the related-url->online-resource function correctly returns
     subtypes for the DMR use cases"
    (let [dmr-case (call-related-url-convert "EXTENDED METADATA" "DMR++")
          dmr-missing-case (call-related-url-convert "EXTENDED METADATA" "DMR++ MISSING DATA")
          not-included-case (call-related-url-convert "EXTENDED METADATA" "not known")
          other-case (call-related-url-convert "DOWNLOAD SOFTWARE""APP")
          nil-case (call-related-url-convert nil nil)]
      (is (= "EXTENDED METADATA : DMR++" dmr-case))
      (is (= "EXTENDED METADATA : DMR++ MISSING DATA" dmr-missing-case))
      (is (= "USER SUPPORT" not-included-case))
      (is (= "USER SUPPORT" other-case))
      (is (= "USER SUPPORT" nil-case)))))
