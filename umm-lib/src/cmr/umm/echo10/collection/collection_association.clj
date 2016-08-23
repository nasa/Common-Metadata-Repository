(ns cmr.umm.echo10.collection.collection-association
  (:require [clojure.data.xml :as x]
            [cmr.common.xml :as cx]
            [cmr.umm.umm-collection :as c]))

(defn xml-elem->CollectionAssociation
  [ca-elem]
  (let [short-name (cx/string-at-path ca-elem [:ShortName])
        version-id (cx/string-at-path ca-elem [:VersionId])]
    (c/map->CollectionAssociation {:short-name short-name
                                   :version-id version-id})))

(defn xml-elem->CollectionAssociations
  [xml-struct]
  (seq (map xml-elem->CollectionAssociation
            (cx/elements-at-path
              xml-struct
              [:CollectionAssociations :CollectionAssociation]))))

(defn generate-collection-associations
  [cas]
  (when (seq cas)
    (x/element
      :CollectionAssociations {}
      (for [ca cas]
        (let [{:keys [short-name version-id]} ca]
          (x/element :CollectionAssociation {}
                     (x/element :ShortName {} short-name)
                     (x/element :VersionId {} version-id)
                     (x/element :CollectionType {} "Input Collection")))))))
