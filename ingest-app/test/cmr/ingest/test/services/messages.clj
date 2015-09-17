(ns cmr.ingest.test.services.messages
  "This tests some of the more complicated functions of cmr.ingest.services.messages"
  (:require [clojure.test :refer :all]
            [cmr.ingest.services.messages :as msg]))

(deftest science-keyword-not-matches-kms-keywords-message-test
  (are [sk expected-attribs-part]
       (= (str "Science keyword " expected-attribs-part " was not a valid keyword combination.")
          (msg/science-keyword-not-matches-kms-keywords (assoc sk :detailed-variable "ignore")))

       {:category "cat"}
       "Category [cat]"

       {:category "cat" :topic "top"}
       "Category [cat] and Topic [top]"

       {:category "cat" :topic "top" :term "ter"}
       "Category [cat], Topic [top], and Term [ter]"

       {:category "cat" :topic "top" :term "ter" :variable-level-1 "var1"}
       "Category [cat], Topic [top], Term [ter], and Variable Level 1 [var1]"

       {:category "cat" :topic "top" :term "ter" :variable-level-1 "var1" :variable-level-2 "var2"}
       "Category [cat], Topic [top], Term [ter], Variable Level 1 [var1], and Variable Level 2 [var2]"

       {:category "cat" :topic "top" :term "ter" :variable-level-1 "var1" :variable-level-2 "var2"
        :variable-level-3 "var3"}
       (str "Category [cat], Topic [top], Term [ter], Variable Level 1 [var1],"
            " Variable Level 2 [var2], and Variable Level 3 [var3]")))