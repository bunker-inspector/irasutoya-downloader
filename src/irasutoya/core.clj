(ns irasutoya.core
  (:gen-class)
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [lambdaisland.uri :as uri]
   [irasutoya.db :as db]))

(def ^:private +irasutoya-url+ "https://www.irasutoya.com")
(def ^:private +page-size+ 150)
(def ^:private +summary-endpt+ "/feeds/posts/summary")

(defn- copy-uri-to-file! [uri file]
  (try
    (with-open [in (io/input-stream uri)
                out (io/output-stream file)]
      (log/infof "[WRITE] -> %s" file)
      (io/copy in out))
    (catch Exception e
      (log/warnf "Could not write file at URI %s, Error: %s" uri e))))

(defn- fetch-and-save-image [{:keys [local-path url]}]
  (if (.exists (java.io.File. local-path))
    (log/infof "File %s arleady exists. Skipping." local-path)
    (copy-uri-to-file! url local-path)))

(defn- get-entries
  [fetch-result]
  (-> fetch-result :feed :entry))

(defn- get-full-size-image-from-thumbnail
  [entry]
  (some-> entry :media$thumbnail :url (str/replace "s72-c" "s400")))

(defn- get-image-name
  [url]
  (-> url uri/uri :path (str/split #"/") last))

(defn- determine-output-dir
  [image-name]
  (if (str/starts-with? image-name "thumbnail")
    "thumbnails"
    "main"))

(defn- fetch
  ([offset]
   (fetch offset +page-size+))
  ([offset page-size]
   {:pre [(> offset 0)]}
   (let [{status :status
          body :body}
         (http/get (str +irasutoya-url+ +summary-endpt+)
                   {:query-params {"alt" "json"
                                   "start-index" (str offset)
                                   "max-results" (str page-size)}})]
     (when (= 200 status)
       (json/read-str body
                      :key-fn keyword)))))

(defn- get-total []
  (-> (fetch 1 0)
      :feed
      :openSearch$totalResults
      :$t
      Integer/parseInt))

(defn- format-entry
  [entry]
  (when-let [url (get-full-size-image-from-thumbnail entry)]
    (let [image-name (get-image-name url)]
     {:title (some-> entry :title :$t)
      :author (some->> entry :author first :name :$t)
      :summary (some-> entry :summary :$t str/trim)
      :url url
      :image-name image-name
      :local-path (str "output/" (determine-output-dir image-name) "/" image-name)
      :tags (some->> entry :category (map :term))})))

(defn- page->offset [page page-size] (inc (* page page-size)))

(defn- get-page
  ([page]
   (get-page page +page-size+))
  ([page page-size]
   (let [offset (page->offset page page-size)]
     (->> (fetch offset page-size)
          get-entries
          (map format-entry)
          (filter some?)
          (map-indexed (fn [idx formatted]
                         (assoc formatted
                                :id (+ offset idx))))))))

(defn- get-page-count
  ([]
   (get-page-count +page-size+))
  ([page-size]
   (-> (get-total) (/ page-size) int inc)))

(defn- ensure-output-dirs []
  (.mkdir (java.io.File. "output"))
  (.mkdir (java.io.File. "output/thumbnails"))
  (.mkdir (java.io.File. "output/main")))

(defn -main [& _]
  (ensure-output-dirs)
  (db/migrate)
  (->> (get-page-count +page-size+)
       range
       (pmap get-page)
       (map db/insert-image-data!)
       (map (partial pmap fetch-and-save-image))
       (map count)
       (reduce +)
       (log/infof "Downloded %d images.")))

(comment
  (def debug (atom {}))
  (add-tap (fn [[k v]] (swap! debug assoc k v)))

  ;; (tap> [:a 1])
  ;; @debug
  ;; => {:a 1}
  )
