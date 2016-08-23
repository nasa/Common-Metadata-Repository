(ns cmr.umm.dif10.collection.characteristic
  "Functions to parse and generate DIF10 Characteristic elements which are used in Platform,
  Instrument and Sensor elements."
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

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

(defn generate-characteristics
  [characteristics]
  (when-not (empty? characteristics)
    (for [{:keys [name description data-type unit value]} characteristics]
      (x/element :Characteristics {}
                 (x/element :Name {} name)
                 (x/element :Description {} description)
                 (x/element :DataType {} data-type)
                 (x/element :Unit {} unit)
                 (x/element :Value {} value)))))
