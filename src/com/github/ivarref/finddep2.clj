(ns com.github.ivarref.finddep2
  (:require [clojure.string :as str]
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

(defn- print-node
  [{:keys [lib coord include reason]} indented {:keys [hide-libs]}]
  (when (and lib (or (= reason :new-top-dep) (not (contains? hide-libs lib))))
    (let [pre (space indented)
          summary (ext/coord-summary lib coord)]
      (println
        (case reason
          :new-top-dep
          (str pre summary)

          (:new-dep :same-version)
          (str pre ". " summary)

          :newer-version
          (str pre ". " summary " " reason)

          (:use-top :older-version :excluded :parent-omitted :superseded) ;; :superseded is internal here
          (str pre "X " summary " " reason)

          ;; fallthrough, unknown reason
          (str pre "? " summary include reason))))))

(defn has-child? [{:keys [children] :as libs} needle-set]
  (assert (set? needle-set))
  (if (nil? children)
    false
    (if (some (fn [needle-child] (contains? children needle-child)) needle-set)
      true
      (some (fn [child] (has-child? child needle-set)) children))))

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
       (print-node tree indented opts'))
     (doseq [child (sort-by :step (vals children))]
       (print-tree child needle-set (+ indented (:indent opts')) opts')))))

(defn find2 [{:keys [force-exit?] :as opts}]
  (utils/require-deps-edn!)
  (let [nam (utils/get-opt opts :name :exit)
        libs (get-lib-tree (utils/get-opt opts :aliases []))
        needles (->> (tree-seq :children (fn [nod] (vals (:children nod)))
                               libs)
                     (mapv :lib)
                     (filter #(str/includes? (str %) (str nam)))
                     (distinct)
                     (into (sorted-set)))]
    (if (= #{} needles)
      (binding [*out* *err*]
        (println (str "No matches found for '" nam "'."))
        (println "Was is a typo?")
        (System/exit 1))
      (print-tree libs needles {}))))