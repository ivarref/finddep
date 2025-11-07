(ns com.github.ivarref.finddep-distinct
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.deps :as deps]
            [clojure.tools.deps.extensions :as ext]
            [clojure.tools.deps.tree :as deps-tree]
            [clojure.tools.deps.util.session :as session]
            [com.github.ivarref.finddep-utils :as utils]))

(defn- space
  [n]
  (apply str (repeat n \space)))

(defn get-lib-tree
  ([aliases master-edn]
   (assert (vector? aliases))
   (let [master-edn (merge {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}
                                        "clojars" {:url "https://repo.clojars.org/"}}}
                           master-edn)
         combined-aliases (deps/combine-aliases master-edn aliases)
         basis (session/with-session
                 (deps/calc-basis master-edn {:resolve-args   (merge
                                                                combined-aliases
                                                                {:trace true})
                                              :classpath-args combined-aliases}))
         libs (-> basis :libs meta :trace (deps-tree/trace->tree))]
     libs))
  ([aliases]
   (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps "deps.edn")
         master-edn (deps/merge-edns [root-edn user-edn project-edn])]
     (get-lib-tree aliases master-edn))))

(def lines (atom (sorted-set)))

(defn- print-node
  [{:keys [lib coord include reason]} indented
   {:keys [not-version excluded match-name color hide-libs]}]
  (assert (string? match-name)
          (str "Expected match-name to be string, was: " (pr-str (type match-name))))
  (assert (boolean? color))
  (assert (boolean? excluded))
  (when (and lib (or (= reason :new-top-dep) (not (contains? hide-libs lib))))
    (let [pre "" #_(space indented)
          summary (ext/coord-summary lib coord)
          colorize (fn [what with-color]
                     (if color
                       (str/replace what match-name (clansi/style match-name with-color))
                       what))
          lin (case reason
                :new-top-dep
                (colorize (str pre summary) :cyan)

                (:new-dep :same-version)
                (colorize (str pre ". " summary) :cyan)

                :newer-version
                (colorize (str pre ". " summary " " reason) :cyan)

                :excluded
                (if excluded
                  (colorize (str pre "X " summary " " reason) :yellow)
                  nil)

                ;; :superseded is internal here
                (:use-top :older-version :parent-omitted :superseded)
                (colorize (str pre "X " summary " " reason) :yellow)

                ;; fallthrough, unknown reason
                (colorize (str pre "? " summary include reason) :red))
          lin (if (and (some? not-version)
                       (= :mvn (ext/coord-type coord))
                       (= not-version (:mvn/version coord)))
                nil
                lin)]
      (when (string? lin)
        (swap! lines conj lin)))))

(defn has-child? [tree needle-set]
  (assert (set? needle-set))
  (->> (tree-seq :children
                 (fn [nod] (vals (:children nod)))
                 tree)
       (mapv :lib)
       (distinct)
       (into (sorted-set))
       (set/intersection needle-set)
       (not= #{})))

(defn print-tree
  "Print the tree to the console.
   Options:
     :indent    Indent spacing (default = 2)
     :hide-libs Set of libs to ignore as deps under top deps, default = #{org.clojure/clojure}"
  ([tree needle-set {:keys [indent] :or {indent 2} :as opts}]
   (print-tree tree needle-set (- 0 indent) opts))
  ([{:keys [children lib] :as tree} needle-set indented opts]
   (let [opts' (merge {:indent 2, :hide-libs '#{org.clojure/clojure}} opts)]
     (when (or (has-child? tree needle-set)
               (contains? needle-set lib))
       (print-node tree indented opts')
       (doseq [child (sort-by :lib (vals children))]
         (print-tree child needle-set (+ indented (:indent opts')) opts'))))))

(defn find2 [{:keys [libs] :as opts}]
  (utils/require-deps-edn!)
  (let [nam (str (utils/get-opt opts :name :exit))
        color (utils/get-opt opts :color true)
        excluded (utils/get-opt opts :show-excluded true)
        focus (utils/get-opt opts :focus nil)
        not-version (utils/get-opt opts :not-version nil)
        libs (or libs (get-lib-tree (utils/get-opt opts :aliases [])))
        libs (if (nil? focus)
               libs
               (->> (tree-seq :children
                              (fn [nod] (vals (:children nod)))
                              libs)
                    (filter #(str/includes? (str (:lib %)) (str focus)))
                    (first)))
        needles (->> (tree-seq :children
                               (fn [nod] (vals (:children nod)))
                               libs)
                     (mapv :lib)
                     (filter #(str/includes? (str %) (str nam)))
                     (distinct)
                     (into (sorted-set)))]
    (if (= #{} needles)
      (binding [*out* *err*]
        (println (str "No matches found for '" nam "'."))
        (println "Was it a typo?")
        (System/exit 1))
      (do
        (print-tree libs
                    needles
                    {:not-version not-version :excluded excluded :color color :match-name nam})
        (doseq [lin @lines]
          (println lin))))))