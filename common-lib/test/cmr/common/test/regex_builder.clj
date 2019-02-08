(ns cmr.common.test.regex-builder
  (:require [clojure.test :refer :all]
            [cmr.common.regex-builder :refer :all]))

(declare regex-examples)

(deftest build-regex-test
  (doseq [{:keys [regex matches non-matches]} regex-examples]
    (let [regex (compile-regex regex)]
      (doseq [match matches]
        (is (re-matches regex match)))
      (doseq [match non-matches]
        (is (not (re-matches regex match)))))))

(def regex-examples
  [{:regex digit
    :matches ["5" "0"]
    :non-matches ["a" "A" "55"]}

   {:regex decimal-number
    :matches ["5" "55" "1234567890" "123.0" "-123.0" "-123.1234567890" "+123.0"]
    :non-matches ["a" "55.5a" "5a" "5-4"]}

   {:regex (group "A" "b" "1")
    :matches ["Ab1"]
    :non-matches ["Ab12" "Ab1Ab1"]}

   {:regex (capture "A" "b" "1")
    :matches ["Ab1"]
    :non-matches ["Ab12" "Ab1Ab1"]}

   {:regex (group "A" (optional "b" "z") "1")
    :matches ["A1" "Abz1"]
    :non-matches ["Ab" "Ab1" "Az1"]}

   {:regex (group "A" (one-or-more "b" "z") "1")
    :matches ["Abz1" "Abzbz1" "Abzbzbzbzbzbz1"]
    :non-matches ["A1"  "Ab" "Ab1" "Az1"]}

   {:regex (group "A" (zero-or-more "b" "z") "1")
    :matches ["A1" "Abz1" "Abzbz1" "Abzbzbzbzbzbz1"]
    :non-matches ["Ab" "Ab1" "Az1"]}

   {:regex (group "A" (choice "b" "z") "1")
    :matches ["Ab1" "Az1"]
    :non-matches ["Ab" "Abz1" "A1"]}

   {:regex (group "A" (n-times 3 "b" "z") "1")
    :matches ["Abzbzbz1"]
    :non-matches ["Abz1" "Abzbz1" "Abbb1"]}

   {:regex (group "A" (n-or-more-times 3 "b" "z") "1")
    :matches ["Abzbzbz1" "Abzbzbzbz1" "Abzbzbzbzbz1"]
    :non-matches ["Abz1" "Abzbz1" "Abbb1"]}

   {:regex (group "A" (n-to-m-times 2 3 "b" "z") "1")
    :matches ["Abzbz1" "Abzbzbz1"]
    :non-matches ["Abz1" "Abzbzbzbz1" "Abbb1"]}])
