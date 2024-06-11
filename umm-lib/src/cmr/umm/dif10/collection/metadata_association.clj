(ns cmr.umm.dif10.collection.metadata-association
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as c]))

(defn xml-elem->MetadataAssociation
  [ca-elem]
  (c/map->CollectionAssociation {:short-name (cx/string-at-path ca-elem [:Entry_ID :Short_Name])
                                 :version-id (cx/string-at-path ca-elem [:Entry_ID :Version])}))

(defn xml-elem->MetadataAssociations
  [xml-struct]
  (seq (map xml-elem->MetadataAssociation
            (cx/elements-at-path
              xml-struct
              [:Metadata_Association]))))

(defn generate-metadata-associations
  [cas]
  (for [ca cas]
    (let [{:keys [short-name version-id]} ca]
      (xml/element :Metadata_Association {}
                 (xml/element :Entry_ID {}
                            (xml/element :Short_Name {} short-name)
                            (xml/element :Version {} version-id))
                 ;; Type is a required field in DIF 10, but CMR UMM does not support it yet.
                 ;; "Input" is one of the enumerations defined for this field by DIF 10 in the
                 ;; schema. Other values are "Dependent" & "Science Associated"
                 (xml/element :Type {} "Input")))))
