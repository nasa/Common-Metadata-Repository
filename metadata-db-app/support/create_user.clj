;;; run with lein exec -p create_user.clj
(require '[cmr.metadata-db.db :as db])

(db/create-user)