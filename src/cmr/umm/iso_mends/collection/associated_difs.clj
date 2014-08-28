(ns cmr.umm.iso-mends.collection.associated-difs
  "Contains functions for parsing and generating the ISO MENDS associated difs"
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.iso-mends.collection.helper :as h]))

(defn xml-elem->associated-difs
  "Returns associated difs from a parsed XML structure"
  [id-elem]
  (let [identifier-elems (cx/elements-at-path
                           id-elem
                           [:citation :CI_Citation :identifier :MD_Identifier])
        dif-elems (filter
                    #(= "DIFEntryId" (cx/string-at-path % [:description :CharacterString]))
                    identifier-elems)]
    (seq (map #(cx/string-at-path % [:code :CharacterString]) dif-elems))))


(defn generate-associated-difs
  [difs]
  (for [dif difs]
    (x/element :gmd:identifier {}
               (x/element :gmd:MD_Identifier {}
                          (h/iso-string-element :gmd:code dif)
                          (h/iso-string-element :gmd:codeSpace "gov.nasa.gcmd")
                          (h/iso-string-element :gmd:description "DIFEntryId")))))
