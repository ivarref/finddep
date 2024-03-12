(ns com.github.ivarref.finddep
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :as jio]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.tools.deps :as deps]
            [clojure.tools.deps.util.session :as session]
            [clojure.tools.gitlibs]
            [clojure.tools.deps.util.maven :as mvn-util]
            [clojure.tools.deps.extensions.git]
            [fzf.core :as fz])
  (:gen-class)
  (:import (Janei Janei)
           (org.eclipse.aether.impl DefaultServiceLocator)))

;DefaultRepositorySystem
(comment
  (do
    (require '[clj-commons.pretty.repl])
    (clj-commons.pretty.repl/install-pretty-exceptions)))

(defn get-libs
  ([master-edn]
   (let [master-edn (merge {:mvn/repos {"central" {:url "https://repo1.maven.org/maven2/"}, "clojars" {:url "https://repo.clojars.org/"}}}
                           master-edn)
         aliases []
         combined-aliases (deps/combine-aliases master-edn aliases)
         basis (session/with-session
                 (deps/calc-basis master-edn {:resolve-args   (merge combined-aliases {:trace true})
                                              :classpath-args combined-aliases}))
         libs (:libs basis)]
     (reduce-kv
       (fn [o k v]
         (assoc o k
                  (update v :dependents (fn [old]
                                          (if (not-empty old)
                                            (into (sorted-set) old)
                                            (sorted-set))))))
       (sorted-map)
       libs)))
  ([]
   (let [{:keys [root-edn user-edn project-edn]} (deps/find-edn-maps "deps.edn")
         master-edn (deps/merge-edns [root-edn user-edn project-edn])]
     (get-libs master-edn))))

(defn all-children [libs lib]
  (reduce-kv
    (fn [o k {:keys [dependents]}]
      (if (contains? dependents lib)
        (into (conj o k) (all-children libs k))
        o))
    (sorted-set)
    libs))

(defn direct-children [libs lib]
  (reduce-kv
    (fn [o k {:keys [dependents]}]
      (if (contains? dependents lib)
        (conj o k)
        o))
    (sorted-set)
    libs))

(defn height [libs lib]
  (let [dependents (get-in libs [lib :dependents] ::none)]
    (if (= ::none dependents)
      (throw (ex-info (str "Lib '" lib "' not found") {:lib lib}))
      (if (empty? dependents)
        1
        (+ 1 (reduce
               max
               0
               (mapv (partial height libs) dependents)))))))

(defn libs-with-needles [libs needles]
  (reduce-kv (fn [o k v]
               (if (or (contains? needles k)
                       (not= #{}
                             (set/intersection (all-children libs k) needles)))
                 (assoc o k (-> v
                                (assoc :height (height libs k))))
                 o))
             (sorted-map)
             libs))

(defn find-needles [libs sym]
  (reduce-kv (fn [o k _]
               (if (str/includes? (str k) (str sym))
                 (conj o k)
                 o))
             (sorted-set)
             libs))

(defn depth [libs lib]
  (+ 1 (reduce
         max
         0
         (mapv (partial depth libs) (direct-children libs lib)))))

;1 s3-wagon-private/s3-wagon-private

;  2 com.amazonaws/aws-java-sdk-sts
;    4 com.amazonaws/aws-java-sdk-core
;      5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

;  2 com.amazonaws/aws-java-sdk-s3
;    4 com.amazonaws/aws-java-sdk-core <-- will be shown later, remove!
;      5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

;    3 com.amazonaws/aws-java-sdk-kms
;      4 com.amazonaws/aws-java-sdk-core
;        5 com.fasterxml.jackson.dataformat/jackson-dataformat-cbor

(defn version-str [x]
  (cond
    (= :mvn (:deps/manifest x))
    (str "{:mvn/version " (pr-str (get x :mvn/version)) "}")

    (some? (:local/root x))
    (str "{:local/root " (pr-str (get x :local/root)) "}")

    (some? (:git/sha x))
    (str "{:git/tag " (pr-str (get x :git/tag))
         " :git/sha \""
         (get x :git/sha)
         "\"}")))

(defn show-tree [libs root indent seen?]
  (let [lib (get libs root)]
    (println (str (str/join "" (repeat (* 2 indent) " "))
                  #_(str seen? " ")
                  #_(:height (get libs root) " ")
                  root
                  " "
                  (version-str lib))))
  (let [children (->> libs
                      (filter (fn [[_k {:keys [dependents]}]]
                                (contains? dependents root)))
                      (sort-by (fn [[k _]] (depth libs k)))
                      (reverse))]
    (doseq [[idx [k _]] (map-indexed vector children)]
      (let [previous-seen (->> (take idx (map first children))
                               (reduce
                                 (fn [o v]
                                   (into o (all-children libs v)))
                                 #{}))
            seen? (if (true? seen?)
                    true
                    (contains? previous-seen k))]
        (when (false? seen?)
          (show-tree libs k (inc indent) seen?))))))

(comment
  (let [libs (get-libs {:deps {'s3-wagon-private/s3-wagon-private {:mvn/version "1.3.5"}}})
        libs (libs-with-needles libs (find-needles libs "jackson-dataformat"))]
    (show-tree libs 's3-wagon-private/s3-wagon-private 0 false)))

(comment
  (let [libs (get-libs {:deps {'slipset/deps-deploy {:mvn/version "0.2.1"}}})
        libs (libs-with-needles libs (find-needles libs "slf4j-api"))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (show-tree libs root 0 false)))))

(comment
  (let [libs (get-libs {:deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}})
        libs (libs-with-needles libs (find-needles libs ""))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (show-tree libs root 0 false)))))

(defn require-deps-edn! []
  (when (not (.exists (jio/file "deps.edn")))
    (binding [*out* *err*]
      (println "Error. Not a tools.deps project. Missing deps.edn"))
    (System/exit 1)))

(defn find [{:keys [name]}]
  (require-deps-edn!)
  (let [libs (get-libs)
        needles (find-needles libs (if (or (= name :all)
                                           (= name :*))
                                     ""
                                     name))
        libs (libs-with-needles libs needles)]
    (if (= needles #{})
      (binding [*out* *err*]
        (println (str "No matches found for '" name "'."))
        (println "Was is a typo?")
        (System/exit 1))
      (let [roots (->> libs
                       (filter (fn [[_k {:keys [dependents]}]]
                                 (= dependents #{})))
                       (sort-by (fn [[k _]] (depth libs k)))
                       (reverse))]
        (doseq [[root _] roots]
          (show-tree libs root 0 false))))))

(defn fzf [_]
  (require-deps-edn!)
  (let [libs (try
               (time (get-libs))
               (catch Throwable t
                 (println "Error during get-libs:")
                 (.printStackTrace t)
                 (throw t)))]
    (if-let [v (fz/fzf {:preview-fn (fn [selected]
                                      (with-out-str (find {:name (str selected)})))}
                       (into [] (mapv str (sort (keys libs)))))]
      (find {:name v})
      (println "Nothing selected, exiting"))))

(defn -main
  "Entrypoint for finddep app"
  [& args]
  (let [locator ^DefaultServiceLocator (mvn-util/make-locator)]
    (.setErrorHandler locator (Janei.)))
    ;(println "reposystem:" (.getService locator RepositorySystem))
    ;(println "locator:" locator))
  ;(println "system is:" (mvn-util/make-system))
  (fzf nil)
  (shutdown-agents))
