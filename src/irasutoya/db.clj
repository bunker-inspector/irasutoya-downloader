(ns irasutoya.db
  (:require [ragtime.jdbc :as ragtime]
            [ragtime.repl :as repl]
            [next.jdbc :as jdbc]
            [honey.sql :as honey]
            [inflections.core :as inflections]))

(def config
  {:dbtype "sqlite"
   :classname "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname "irasutoya.db"
   :dbname "irasutoya.db"})

(defn migrate []
  (repl/migrate
   {:datastore (ragtime/sql-database config)
    :migrations (ragtime/load-resources "migrations")}))

(defn- clj-ify [result]
  (some->> result
           vec
           (map (fn [[a b]]
                  [(inflections/hyphenate a) b]))
           (into {})))

(defn execute-one! [sql]
  (clj-ify (jdbc/execute-one! config (honey/format sql) {:return-keys true})))

(defn insert-all! [table values]
  (execute-one! {:insert-into table
                 :values values}))

(defn- tag-data
  [{:keys [id tags]}]
  (map #(hash-map :image-id id :value %) tags))

(defn insert-image-data!
  [entries]
  (insert-all! :images (map #(dissoc % :tags) entries))
  (insert-all! :tags (mapcat tag-data entries))
  entries)
