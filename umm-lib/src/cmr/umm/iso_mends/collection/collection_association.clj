(ns cmr.umm.iso-mends.collection.collection-association
  "Contains functions for parsing and generating the ISO MENDS collection associations"
  (:require
   [clojure.data.xml :as xml]
   [cmr.common.xml :as cx]
   [cmr.umm.umm-collection :as c]
   [cmr.umm.iso-mends.collection.helper :as h]))

(defn xml-elem->CollectionAssociation
  [ca-elem]
  (let [short-name (cx/string-at-path ca-elem [:CI_Citation :title :CharacterString])
        version-id (cx/string-at-path ca-elem [:CI_Citation :edition :CharacterString])]
    (c/map->CollectionAssociation {:short-name short-name
                                   :version-id version-id})))

(defn xml-elem->CollectionAssociations
  [id-elem]
  (seq (map xml-elem->CollectionAssociation
            (cx/elements-at-path
              id-elem
              [:aggregationInfo :MD_AggregateInformation :aggregateDataSetName]))))

(defn generate-collection-associations
  [cas]
  (for [ca cas]
    (xml/element
      :gmd:aggregationInfo {}
      (xml/element
        :gmd:MD_AggregateInformation {}
        (xml/element
          :gmd:aggregateDataSetName {}
          (xml/element :gmd:CI_Citation {}
                     (h/iso-string-element :gmd:title (:short-name ca))
                     (xml/element :gmd:date {:gco:nilReason "unknown"})
                     (h/iso-string-element :gmd:edition (:version-id ca))
                     (h/iso-string-element :gmd:otherCitationDetails "Extra data")))
        (xml/element
          :gmd:aggregateDataSetIdentifier {}
          (xml/element :gmd:MD_Identifier {}
                     (h/iso-string-element :gmd:code (:short-name ca))))
        (xml/element
          :gmd:associationType {}
          (xml/element
            :gmd:DS_AssociationTypeCode
            {:codeList "http://www.ngdc.noaa.gov/metadata/published/xsd/schema/resources/Codelist/gmxCodelists.xml#DS_AssociationTypeCode"
             :codeListValue "Input Collection"}
            "Input Collection"))))))
