(ns cmr.metadata-db.migrations.034-generate-tag-associations-from-tags
  (:require
   [clojure.edn :as edn]
   [clojure.java.jdbc :as j]
   [clojure.string :as str]
   [cmr.metadata-db.services.concept-service :as cs]
   [config.mdb-migrate-helper :as h]
   [config.mdb-migrate-config :as config]))

(defn up
  "Migrates the database up to version 34."
  []
  (println "cmr.metadata-db.migrations.034-generate-tag-associations-from-tags up...")
  (println (str "Changing cmr_tag_associations.associated_revision_id to allow NULL because "
                "associated revision ids should be optional on tag associations."))
  (h/sql "alter table cmr_tag_associations modify (associated_revision_id NULL)")
  (h/sql "alter table cmr_tag_associations add tag_key varchar(1030) NOT NULL")
  (h/sql "CREATE INDEX tag_assoc_tkri ON cmr_tag_associations (tag_key, revision_id)")
  (let [context {:system {:db (config/db)}}]
    (doseq [{:keys [concept_id revision_id]} (h/query "select concept_id, max(revision_id) as revision_id from cmr_tags group by concept_id")]
      (println (format "Processing tag [%s]" concept_id))
      (let [revision-id (long revision_id)
            tag-concept (cs/get-concept context concept_id revision-id)]
        (when-not (:deleted tag-concept)
          (let [tag (edn/read-string (:metadata tag-concept))
                new-tag-metadata (pr-str (dissoc tag :associated-concept-ids))
                tag-concept (-> tag-concept
                                (assoc :metadata new-tag-metadata :revision-id (inc revision-id))
                                (dissoc :transaction-id))]

            ;; Save the tag associations.
            (doseq [concept-id (:associated-concept-ids tag)]
              (println (format "Creating association with concept-id [%s]" concept-id))
              (let [user-id (:user-id tag)
                    tag-key (:tag-key tag)
                    native-id (str/join "/" [tag-key concept-id])
                    ta-metadata (pr-str {:associated-concept-id concept-id
                                         :tag-key tag-key
                                         :originator-id (:originator-id tag)})
                    extra-fields {:associated-concept-id concept-id
                                  :tag-key tag-key}

                    tag-association-concept {:concept-type :tag-association
                                             :user-id user-id
                                             :format "application/edn"
                                             :native-id native-id
                                             :metadata ta-metadata
                                             :extra-fields extra-fields}]
                (cs/save-concept-revision context tag-association-concept)))

            ;; Save the tag without the associated concept ids in the metadata
            (cs/save-concept-revision context tag-concept)))))))

(defn down
  "Migrates the database down from version 34."
  []
  (println "cmr.metadata-db.migrations.034-generate-tag-associations-from-tags down...")
  (let [context {:system {:db (config/db)}}]
    (doseq [{:keys [concept_id revision_id]} (h/query "select concept_id, max(revision_id) as revision_id from cmr_tags group by concept_id")]
      (println (format "Processing tag [%s]" concept_id))
      (let [revision-id (long revision_id)
            tag-concept (cs/get-concept context concept_id revision-id)]
        (when-not (:deleted tag-concept)
          (let [tag-key (:native-id tag-concept)
                associated-concept-ids (->> (h/query (format "select associated_concept_id from cmr_tag_associations where native_id like '%s%%'"
                                                             (str tag-key "/")))
                                            (map :associated_concept_id))
                metadata (edn/read-string (:metadata tag-concept))
                new-metadata (pr-str (assoc metadata :associated-concept-ids associated-concept-ids))
                tag-concept (-> tag-concept
                             (assoc :metadata new-metadata :revision-id (inc revision-id))
                             (dissoc :transaction-id))]
            (cs/save-concept-revision context tag-concept)))))
    (h/sql "delete from cmr_tag_associations")
    (h/sql "alter table cmr_tag_associations drop column tag_key")
    (h/sql "alter table cmr_tag_associations modify (associated_revision_id NOT NULL)")))
