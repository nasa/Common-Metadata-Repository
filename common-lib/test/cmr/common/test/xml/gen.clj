(ns cmr.common.test.xml.gen
  (:require [cmr.common.xml.gen :refer [xml]]
            [clojure.test :refer :all]
            clojure.data.xml))

(deftest test-xml-gen
  (is (= (clojure.data.xml/parse-str
          (xml [:foo
                (list
                 (list
                  [:zort [:troz "stuff"] nil [:zing]])
                 [:empty])
                [:bar "bat"]]))
         (clojure.data.xml/parse-str
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
           <foo>
             <zort>
               <troz>stuff</troz>
             </zort>
             <bar>bat</bar>
           </foo>"))))
