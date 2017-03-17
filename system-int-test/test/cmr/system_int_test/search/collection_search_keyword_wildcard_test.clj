(ns cmr.system-int-test.search.collection-search-keyword-wildcard-test
  "Integration test to test the combinations of keyword length/number of wildcard
  limits. "
  (:require
   [clojure.test :refer :all]
   [clojure.string :as str]
   [cmr.search.data.query-to-elastic :as query-to-elastic]
   [cmr.system-int-test.utils.search-util :as search]))

(defn- create-keyword-wildcard-string
 "Create a keyword query string with x number of unique keywords of length len
 with a wildcard at the end."
 [x len]
 (let [a-val (int \a)
       base-vec (vec (repeat (dec len) a-val))]
  (for [x (range x)
        :let [increment (mod x 26)
              index (int (/ x 26))]
        :when (< index (dec len))
        :let [distinct-vec (assoc base-vec index (+ a-val increment))]]
   (str (apply str (map char distinct-vec)) "*"))))

(defn- test-limit
 "Test function for testing number of keywords with wildcards and max keyword
 length combo"
 [num-keywords max-keyword-length]
 (let [keyword-strings (create-keyword-wildcard-string num-keywords max-keyword-length)
       keyword-string (str/join " " keyword-strings)]
  (search/find-refs :collection {:keyword keyword-string})))

#_(deftest search-keyword-wildcard-limits-test
   ;; There is a total limit on max wildcards, but we want to test all the
   ;; combinations even if they go over that, so overwite that number to the
   ;; maximum + 1
   (let [orig-keyword-wildcard-max query-to-elastic/KEYWORD_WILDCARD_NUMBER_MAX]
    (intern 'cmr.search.data.query-to-elastic 'KEYWORD_WILDCARD_NUMBER_MAX
     (inc (#'query-to-elastic/get-max-kw-number-allowed 1)))

    ;; For each keyword length to 241, test that the maximum in the mapping is
    ;; correct and does not result in a 500 error
    (let [failed-atom (atom false)]
     (doseq [x (range 3 241)
             :when (not @failed-atom) ;; Stop test on failure
             :let [max-num-keywords (#'query-to-elastic/get-max-kw-number-allowed x)
                   result (test-limit max-num-keywords x)]]
       (println "Testing keyword length " x)
       (when-not
        (is (or (= 200 (:status result)) (nil? (:status result)))
            (format
             (str "Keyword wildcard limit of %d is incorrect for max keyword string "
                  "length of %d")
             max-num-keywords x))
        (reset! failed-atom true))))

    ;; Set KEYWORD_WILDCARD_NUMBER_MAX back to the original value
    (intern 'cmr.search.data.query-to-elastic 'KEYWORD_WILDCARD_NUMBER_MAX
     orig-keyword-wildcard-max)))


(comment
 (test-limit 60 7))
