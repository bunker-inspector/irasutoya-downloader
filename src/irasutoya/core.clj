(ns irasutoya.core
  (:gen-class)
  (:require
   [clj-http.client :as http]
   [clojure.data.json :as json]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [lambdaisland.uri :as uri]))

(def +irasutoya-url+ "https://www.irasutoya.com")
(def +irasutoya-uri-comps+ (uri/uri +irasutoya-url+))
(def +default-endpt+ "/feeds/posts/default")
(def +summary-endpt+ "/feeds/posts/summary")

(defn copy-uri-to-file! [uri file]
  (try
    (with-open [in (io/input-stream uri)
                out (io/output-stream file)]
      (log/infof "[WRITE] -> %s" file)
      (io/copy in out))
    (catch Exception e
      (log/warnf "Could not write file at URI %s, Error: %s" uri e))))

(defn process-image [{categories :category
                      {url :url} :media$thumbnail}]
  (let [image-name (-> url uri/uri :path (str/split #"/") last)
        image-folder (if (str/starts-with? image-name "thumbnail")
                       "thumbnails"
                       "main")
        image-path (str "output/" image-folder "/" image-name)]
    (log/infof "Found tags for filename %s: %s"
               image-path
               (str/join "," (map :term categories)))
    (if (.exists (java.io.File. image-path))
      (log/infof "File %s arleady exists. Skipping." image-path)
      (copy-uri-to-file! url image-path))))

(defn entries
  [fetch-result]
  (-> fetch-result :feed :entry))

(defn links
  [entries]
  (map (comp :url :media$thumbnail) entries))

(defn- fetch
  ([start-index]
   (fetch start-index 24))
  ([start-index results]
   {:pre [(> start-index 0)]}
   (let [{status :status
          body :body}
         (http/get (str +irasutoya-url+ +summary-endpt+)
                   {:query-params {"alt" "json"
                                   "start-index" (str start-index)
                                   "results" (str results)}})]
     (when (= 200 status)
       (json/read-str body
                      :key-fn keyword)))))

(defn- get-total []
  (-> (fetch 1 0)
      :feed
      :openSearch$totalResults
      :$t
      Integer/parseInt))

(def +page-size+ 24)

(defn -main []
  (.mkdir (java.io.File. "output"))
  (.mkdir (java.io.File. "output/thumbnails"))
  (.mkdir (java.io.File. "output/main"))

  (let [pages (/ (get-total) +page-size+)]
    (some->> pages
             range
             (map (partial * +page-size+))
             (map inc)
             (pmap (fn [offset]
                     (->> (fetch offset +page-size+)
                          entries
                          (map (fn [s] (update-in s
                                                  [:media$thumbnail :url]
                                                  #(str/replace % "s72-c" "s400"))))
                          (map process-image)
                          dorun)))
             dorun)))
