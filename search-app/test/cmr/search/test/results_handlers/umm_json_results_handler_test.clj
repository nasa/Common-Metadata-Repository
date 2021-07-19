(ns cmr.search.test.results-handlers.umm-json-results-handler-test
  (:require
   [clojure.test :refer :all]
   [cmr.common.util :as util :refer [are3]]
   [cmr.search.results-handlers.umm-json-results-handler :as umm-results]))

(deftest supported-version-test
  (are3 [version min-ver max-ver valid?]
        (is (= valid? (umm-results/supported-version? version min-ver max-ver)))

        "min has more specification (but exceeds min)"
        "1.2" "1.1.5" nil true

        "min has more specification (but does not meet min)"
        "1.9" "2.0.17" nil false

        "target has more specificiation than min (but exceeds min)"
        "1.14.1" "1.14" nil true

        "target has more specificiation than min (but does not meet)"
        "2.15.4" "2.16" nil false

        "target does not meet min at major"
        "1.0.1" "2.0.1" nil false

        "target does not meet min at minor"
        "1.3.1" "1.4.1" nil false

        "target does not meet min at patch"
        "1.16.1" "1.16.2" nil false

        "target exceeds min at major"
        "3.0.1" "2.0.1" nil true

        "target exceeds min at minor"
        "1.42.0" "1.41.1" nil true

        "target exceeds min at patch"
        "1.16.11" "1.16.10" nil true

        "target equals min"
        "1.0.1" "1.0.1" nil true

        "target is in between"
        "1.5.1" "0.9.9" "1.9.9" true

        "target equals max"
        "1.9.9" "0.0.1" "1.9.9" true

        "target exceeds max"
        "1.3.1" "0.9.9" "1.2.0" false))
