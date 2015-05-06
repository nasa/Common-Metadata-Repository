(ns cmr.umm.dif10.collection.characteristic
  (:require [cmr.common.xml :as cx]
            [cmr.umm.collection :as c]))

(defn xml-elem->Characteristic
  [char-elem]
  (let [name (cx/string-at-path char-elem [:Name])
        description (cx/string-at-path char-elem [:Description])
        data-type (cx/string-at-path char-elem [:DataType])
        unit (cx/string-at-path char-elem [:Unit])
        value (cx/string-at-path char-elem [:Value])]
    (c/->Characteristic name description data-type unit value)))

(defn xml-elem->Characteristics
  [platform-element]
  (seq (map xml-elem->Characteristic
            (cx/elements-at-path
              platform-element
              [:Characteristics]))))
