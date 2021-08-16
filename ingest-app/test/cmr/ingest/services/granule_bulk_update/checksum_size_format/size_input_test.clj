(ns cmr.ingest.services.granule-bulk-update.checksum-size-format.size-input-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.ingest.services.granule-bulk-update.size.echo10 :as size]))

(deftest update-size-failures
  (testing "various input failures for input on echo10 size updates using (get-sizes) "
    (are3 [input]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"invalid data specified"
                            (#'size/get-sizes input)))

      "Neither value an integer or double"
      "foo,bar-32"

      "2nd value not an integer or double"
      "1000,1.0MB"

      "1st value not an integer or double"
      "1000MB,1"

      "2 doubles"
      "1000.0,12312.22"

      "2 ints"
      "1000,2000"

      "Too many values"
      "1000,2000.123,3000.0MB"

      "Empty input"
      "")))
