(ns cmr.umm.echo10.granule.characteristic-ref
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-granule :as g]))

(defn xml-elem->CharacteristicRef
  [char-elem]
  (let [name (cx/string-at-path char-elem [:Name])
        value (cx/string-at-path char-elem [:Value])]
    (g/map->CharacteristicRef {:name name
                               :value value})))

(defn xml-elem->CharacteristicRefs
  [parent-element]
  (seq (map xml-elem->CharacteristicRef
            (cx/elements-at-path
              parent-element
              [:Characteristics :Characteristic]))))

(defn generate-characteristic-refs
  [characteristic-refs]
  (when (seq characteristic-refs)
    (x/element
      :Characteristics {}
      (for [{:keys [name value]} characteristic-refs]
        (x/element :Characteristic {}
                   (x/element :Name {} name)
                   (x/element :Value {} value))))))
