(ns cmr.system-int-test.utils.fast-xml
  "Reimplements some of the methods from clojure.data.xml to improve the performance slightly. This
  helps overall test performance. I found that clojure.data.xml created new input factories on
  every parse. Caching it is safe when using within a test and helps performance somewhat."
  (:require
   [clojure.data.xml :as x])
  (:import
   (java.io StringReader)
   (com.sun.xml.internal.stream XMLInputFactoryImpl)))

(def ^XMLInputFactoryImpl cached-xml-input-factory
  (#'x/new-xml-input-factory {:coalescing true}))

(defn parse-str
  "Similar to clojure.data.xml/parse-str. Parses the passed in string to Clojure data structures."
  [s]
  ;; This also avoids reflection that clojure.data.xml does.
  (let [sreader (.createXMLStreamReader cached-xml-input-factory (StringReader. s))]
    (x/event-tree (#'x/pull-seq sreader))))

(comment

 (x/parse-str "<foo><hello>jason</hello></foo>")
 (parse-str "<foo><hello>jason</hello></foo>"))
