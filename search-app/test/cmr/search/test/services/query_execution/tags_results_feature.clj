(ns cmr.search.test.services.query-execution.tags-results-feature
  (:require [clojure.test :refer :all]
            [cmr.search.services.query-execution.tags-results-feature :as t]))

(deftest match-patterns-test
  (testing "value matches patterns"
    (are [value patterns]
         (#'t/match-patterns? value patterns)

         ;; exact matches
         "abc" ["abc"]
         "gov.nasa.cmr.spectrum" ["gov.nasa.cmr.spectrum"]
         "ab*" ["ab*"]
         "ab?" ["ab?"]
         "ab.*" ["ab.*"]
         "ab[c]" ["ab[c]"]
         "(+-&:^/{}\\|&!~[])" ["(+-&:^/{}\\|&!~[])"]


         ;; wildcards
         "abc" ["a*"]
         "abc" ["ab?"]
         "gov.nasa.cmr.spectrum" ["gov.*"]
         "gov.nasa.cmr.spectrum" ["*spectrum"]
         "(+-&:^/{}\\|&!~[])" ["*"]

         ;; multiple
         "gov.nasa.cmr.spectrum" ["no-match" "gov.nasa.cmr.spectrum"]
         "gov.nasa.cmr.spectrum" ["no-match" "gov*"]
         "gov.nasa.cmr.spectrum" ["gov*" "no-match"])))
