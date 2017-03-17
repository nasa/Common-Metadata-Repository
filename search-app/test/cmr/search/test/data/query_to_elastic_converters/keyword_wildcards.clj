(ns cmr.search.test.data.query-to-elastic-converters.keyword-wildcards
  "This test tests the pure functions get-validate-keyword-wildcards-msg"
  (:require
    [clojure.test :refer :all]
    [cmr.common.util :as u :refer [are3]]
    [cmr.search.data.query-to-elastic :as query-to-elastic]))

(deftest get-validate-keyword-wildcards-msg-test
  (are3 [exp-msg msg-retrieved]
        (is (= exp-msg msg-retrieved))

        "Testing number of keywords with wildcards exceeds the max"
        "Max number of keywords with wildcard allowed is 30"
        (#'query-to-elastic/get-validate-keyword-wildcards-msg
          ["0*" "1*" "2*" "3*" "4*" "5*" "6*" "7*" "8*" "9*" "10*" "11*" "12*" "13*" "14*" "15*" "16*" "17*" "18*" "19*" "20*" "21*" "22*" "23*" "24*" "25*" "26*" "27*" "28*" "29*" "30?"])

        "Testing number of keywords with wildcards exceeds the limit for the given max keyword string length "
        "The CMR permits a maximum of 22 keywords with wildcards in a search, given the max length of the keyword being 22. Your query contains 29 keywords with wildcards"
        (#'query-to-elastic/get-validate-keyword-wildcards-msg
          ["000000000000000000000*" "1*" "2*" "3*" "4*" "5*" "6*" "7*" "8*" "9*" "10*" "11*" "12*" "13*" "14*" "15*" "16*" "17*" "18*" "19*" "20*" "21*" "22*" "23*" "24*" "25*" "26?" "27?" "28?"])

        "Testing number of keywords with wildcards passed the validation"
        nil
        (#'query-to-elastic/get-validate-keyword-wildcards-msg
          ["000000000000000000000*" "1*" "2*" "3*" "4*" "5*" "6*" "7*" "8*" "9*" "10*" "11*" "12*" "13*" "14*" "15*" "16*" "17*" "18*"])))
