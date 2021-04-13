(ns irasutoya.core
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

(defn- is-some-bullshit? [image-name]
  (or (str/starts-with? image-name "twitter")
      (str/starts-with? image-name "line_")
      (str/includes? image-name "searchbtn")
      (str/includes? image-name "sidebar")
      (str/includes? image-name "button")
      (str/includes? image-name "navigation")))

(defn- is-character? [image-name]
  (or (str/starts-with? image-name "alphabet_")
      (str/starts-with? image-name "capital_")
      (str/starts-with? image-name "lower_")
      (str/starts-with? image-name "hiragana_")
      (str/starts-with? image-name "katakana_")
      (str/starts-with? image-name "number_")
      (str/starts-with? image-name "hoka_")
      (str/starts-with? image-name "hoka2_")
      (str/starts-with? image-name "roman_number")
      (str/starts-with? image-name "number_kanji")
      (str/starts-with? image-name "paint_lower_")
      (str/starts-with? image-name "paint_capital_")
      (str/starts-with? image-name "paint_hiragana")
      (str/starts-with? image-name "paint_katakana")
      (str/starts-with? image-name "paint_number_")
      (str/starts-with? image-name "paint_hoka")))

(defn process-image [{categories :category
                      {url :url} :media$thumbnail
                      :as entry}]
  (let [image-name (-> url uri/uri :path (str/split #"/") last)
        image-folder (cond
                       (str/includes? image-name "banner") "banners"
                       (str/includes? image-name "icon") "icons"
                       (str/includes? image-name "logo") "logos"
                       (str/includes? image-name "thumbnail") "thumbnails"
                       (is-character? image-name) "characters"
                       (is-some-bullshit? image-name) "bs"
                       :else "main")
        image-path (str "output/" image-folder "/" image-name)]
    (log/infof "Found tags for filename %s: %s"
               image-path
               (str/join "," (map :term categories)))
    (when-not (.exists (java.io.File. image-path))
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

(defn- is-image? [uri]
  (let [uri (str/lower-case uri)]
    (or
     (str/ends-with? uri ".png")
     (str/ends-with? uri ".jpeg")
     (str/ends-with? uri ".jpg"))))

(def +page-size+ 24)

(defn -main []
  (.mkdir (java.io.File. "output"))
  (.mkdir (java.io.File. "output/banners"))
  (.mkdir (java.io.File. "output/icons"))
  (.mkdir (java.io.File. "output/logos"))
  (.mkdir (java.io.File. "output/characters"))
  (.mkdir (java.io.File. "output/thumbnails"))
  (.mkdir (java.io.File. "output/bs"))
  (.mkdir (java.io.File. "output/main"))

  (let [total (get-total)]
    (loop [offset 1]
     (->> (fetch offset +page-size+)
          entries
          (map (fn [s] (update-in s
                                  [:media$thumbnail :url]
                                  #(str/replace % "s72-c" "s400"))))
          (map process-image)
          doall)
     (when (< offset total)
       (recur (+ offset +page-size+))))))
