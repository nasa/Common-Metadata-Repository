(ns cmr.common.test.xml.gen
  (:require 
   [clojure.test :refer [deftest is]]
   [cmr.common.xml :as cx]
   [cmr.common.xml.gen :refer [xml]]))

(deftest test-xml-gen
  (is (= (cx/parse-str
          (xml [:foo
                (list
                 (list
                  [:zort [:troz "stuff"] nil [:zing]])
                 [:empty])
                [:bar "bat"]]))
         (cx/parse-str
          "<?xml version=\"1.0\" encoding=\"UTF-8\"?>
           <foo>
             <zort>
               <troz>stuff</troz>
             </zort>
             <bar>bat</bar>
           </foo>"))))
