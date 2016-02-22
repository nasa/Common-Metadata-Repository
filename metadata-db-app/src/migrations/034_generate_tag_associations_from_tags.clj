(ns migrations.034-generate-tag-associations-from-tags
  (:require [clojure.java.jdbc :as j]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [config.migrate-config :as config]
            [config.mdb-migrate-helper :as h]
            [cmr.metadata-db.services.concept-service :as cs]))

(defn up
  "Migrates the database up to version 34."
  []
  (println "migrations.034-generate-tag-associations-from-tags up...")
  (println "Changing cmr_tag_associations.associated_revision_id to allow NULL")
  (h/sql "alter table cmr_tag_associations modify (associated_revision_id NULL)")
  (let [context {:system {:db (config/db)}}]
    (doseq [{:keys [concept_id revision_id]} (h/query "select concept_id, max(revision_id) as revision_id from cmr_tags group by concept_id")]
      (println (format "Processing tag [%s]" concept_id))
      (let [revision-id (long revision_id)
            tag (cs/get-concept context concept_id revision-id)]
        (when-not (:deleted tag)
          (let [metadata (edn/read-string (:metadata tag))
                new-metadata (pr-str (dissoc metadata :associated-concept-ids))
                tag (-> tag
                        (assoc :metadata new-metadata :revision-id (inc revision-id))
                        (dissoc :transaction-id))]

            ;; Save the tag associations.
            (doseq [concept-id (:associated-concept-ids metadata)]
              (println (format "Creating association with concept-id [%s]" concept-id))
              (let [user-id (:user-id tag)
                    tag-key (:native-id tag)
                    native-id (str/join (char 29) [tag-key concept-id])
                    ta-metadata (pr-str {:associated-concept-id concept-id
                                         :tag-key (:tag-key metadata)})
                    extra-fields {:associated-concept-id concept-id}

                    tag-association {:concept-type :tag-association
                                     :user-id user-id
                                     :format "application/edn"
                                     :native-id native-id
                                     :metadata ta-metadata
                                     :extra-fields extra-fields}]
                (cs/save-concept-revision context tag-association)))

            ;; Save the tag without the associated concept ids in the metadata
            (cs/save-concept-revision context tag)))))))

(defn down
  "Migrates the database down from version 34."
  []
  (println "migrations.034-generate-tag-associations-from-tags down...")
  (let [context {:system {:db (config/db)}}]
    (doseq [{:keys [concept_id revision_id]} (h/query "select concept_id, max(revision_id) as revision_id from cmr_tags group by concept_id")]
      (println (format "Processing tag [%s]" concept_id))
      (let [revision-id (long revision_id)
            tag (cs/get-concept context concept_id revision-id)]
        (when-not (:deleted tag)
          (let [tag-key (:native-id tag)
                associated-concept-ids (->> (h/query (format "select associated_concept_id from cmr_tag_associations where native_id like '%s%%'"
                                                             (str tag-key (char 29))))
                                            (map :associated_concept_id))
                metadata (edn/read-string (:metadata tag))
                new-metadata (pr-str (assoc metadata :associated-concept-ids associated-concept-ids))
                tag (-> tag
                        (assoc :metadata new-metadata :revision-id (inc revision-id))
                        (dissoc :transaction-id))]
            (println "TAG" tag)
            (cs/save-concept-revision context tag)))))
    (h/sql "delete from cmr_tag_associations")
    (h/sql "alter table cmr_tag_associations modify (associated_revision_id NOT NULL)")))
