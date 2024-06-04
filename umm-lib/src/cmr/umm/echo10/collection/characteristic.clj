(ns cmr.umm.echo10.collection.characteristic
  (:require [clojure.data.xml :as xml]
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
              [:Characteristics :Characteristic]))))

(defn generate-characteristics
  [characteristics]
  (when-not (empty? characteristics)
    (xml/element
      :Characteristics {}
      (for [characteristic characteristics]
        (let [{:keys [name description data-type unit value]} characteristic]
          (xml/element :Characteristic {}
                     (xml/element :Name {} name)
                     (xml/element :Description {} description)
                     (xml/element :DataType {} data-type)
                     (xml/element :Unit {} unit)
                     (xml/element :Value {} value)))))))
