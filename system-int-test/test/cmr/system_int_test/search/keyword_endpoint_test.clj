(ns cmr.system-int-test.search.keyword-endpoint-test
  "Integration test for CMR search endpoint returning GCMD Keywords"
  (:require [clojure.test :refer :all]
            [cmr.common.util :as util]
            [cmr.system-int-test.utils.search-util :as search]))

; (deftest get-keywords-test
;   (testing "Science keywords"
;     (let [expected-keywords []]
;       (is (= {:status 200 :results expected-keywords}
;              (search/get-keywords-by-keyword-scheme :science_keywords)))))
;   (testing "Archive centers")
;   (testing "Providers are an alias for archive centers")
;   (testing "Platforms")
;   (testing "Instruments"))

(def expected-hierarchy
  "Maps the keyword scheme to the expected hierarchy for that scheme."
  {:archive_centers []
   :science_keywords []
   :platforms []
   :instruments []})

(deftest get-keywords-test
  (util/are2
    [keyword-scheme expected-keywords]
    (= {:status 200 :results expected-keywords}
       (search/get-keywords-by-keyword-scheme keyword-scheme))

    "Testing correct keyword hierarchy returned for science keywords."
    :science_keywords (:science_keywords expected-hierarchy)

    ; "Testing correct keyword hierarchy returned for archive centers."
    ; :archive_centers (:archive_centers expected-hierarchy)

    "Testing providers is an alias for archive centers."
    :providers (:archive_centers expected-hierarchy)

    "Testing correct keyword hierarchy returned for platforms."
    :platforms (:platforms expected-hierarchy)

    "Testing correct keyword hierarchy returned for instruments."
    :instruments (:instruments expected-hierarchy)))


; (testing "Science keywords"
;   (let [expected-keywords []]
; (testing "Archive centers")
; (testing "Providers are an alias for archive centers")
; (testing "Platforms")
; (testing "Instruments"))