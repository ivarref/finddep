(ns com.github.ivarref.find-dep-test
  (:require [clj-commons.pretty.repl]
            [clojure.java.io :as jio]
            [clojure.string :as str]
            [clojure.test :as t]
            [com.github.ivarref.finddep :as fd]))

(clj-commons.pretty.repl/install-pretty-exceptions)

(t/deftest basic
  (let [libs (fd/get-libs [] {:deps {'s3-wagon-private/s3-wagon-private {:mvn/version "1.3.5"}}})]
    (t/is (= #{'com.fasterxml.jackson.core/jackson-annotations
               'com.fasterxml.jackson.core/jackson-core
               'com.fasterxml.jackson.core/jackson-databind
               'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor}
             (fd/find-needles libs 'jackson)))
    (t/is (= #{'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor}
             (fd/find-needles libs 'jackson-dataformat)))
    (let [libs (fd/libs-with-needles libs (fd/find-needles libs 'jackson-dataformat))]
      (t/is (= 1 (fd/depth libs 'com.fasterxml.jackson.dataformat/jackson-dataformat-cbor)))
      (t/is (= 2 (fd/depth libs 'com.amazonaws/aws-java-sdk-core)))
      (t/is (= 3 (fd/depth libs 'com.amazonaws/aws-java-sdk-kms)))
      (t/is (= 3 (fd/depth libs 'com.amazonaws/aws-java-sdk-sts)))
      (t/is (= 4 (fd/depth libs 'com.amazonaws/aws-java-sdk-s3)))
      (t/is (= 5 (fd/depth libs 's3-wagon-private/s3-wagon-private))))))

(t/deftest can-print-git-tag
  (let [libs (fd/get-libs [] {:deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}})
        libs (fd/libs-with-needles libs (fd/find-needles libs ""))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (let [s (with-out-str (fd/show-tree libs root 0 false))
              f-line (first (str/split-lines s))]
          (t/is (true?
                  (str/starts-with?
                    f-line
                    "io.github.cognitect-labs/test-runner {:git/tag \"v0.5.0\""))))))))

(t/deftest can-print-local-root
  (let [libs (fd/get-libs [] {:deps {'local/root {:local/root (.getAbsolutePath (jio/file "."))}}})
        libs (fd/libs-with-needles libs (fd/find-needles libs ""))]
    (doseq [[root {:keys [dependents]}] libs]
      (when (= #{} dependents)
        (let [s (with-out-str (fd/show-tree libs root 0 false))]
          (t/is (true? (str/starts-with?
                         (first (str/split-lines s))
                         "local/root {:local/root \""))))
        #_(with-out-str (fd/show-tree libs root 0 false))))))

(t/deftest alias-support-1
  (let [libs (fd/get-libs [] {:deps {'org.slf4j/slf4j-nop {:mvn/version "2.0.7"}}
                              :aliases {:test {:extra-deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}}}})
        libs (fd/libs-with-needles libs (fd/find-needles libs ""))]
    (t/is (false? (contains? (into #{} (keys libs)) 'io.github.cognitect-labs/test-runner)))))

(t/deftest alias-support-2
  (let [libs (fd/get-libs [:test] {:deps {'org.slf4j/slf4j-nop {:mvn/version "2.0.7"}}
                                   :aliases {:test {:extra-deps {'io.github.cognitect-labs/test-runner {:git/tag "v0.5.0" :git/sha "b3fd0d2"}}}}})
        libs (fd/libs-with-needles libs (fd/find-needles libs ""))]
    (t/is (true? (contains? (into #{} (keys libs)) 'io.github.cognitect-labs/test-runner)))))

#_(t/deftest basic
    (let [libs (fd/get-libs {:deps {'clj-http/clj-http {:mvn/version "3.12.3"}}})
          deps (into (sorted-set) (keys libs))]
      (t/is (true? (contains? deps 'clj-http/clj-http)))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/show-roots libs 0 ['riddley/riddley]))))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/find-and-show "riddley"))))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/find-and-show "ridd"))))
      (t/is (= "Zero matches for abc\n"
               (with-out-str (fd/find-and-show "abc"))))))


#_(t/deftest height
    (let [libs (fd/get-libs {:deps {'clj-http/clj-http {:mvn/version "3.12.3"}}})
          deps (into (sorted-set) (keys libs))]
      (t/is (true? (contains? deps 'clj-http/clj-http)))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/show-roots libs 0 ['riddley/riddley]))))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/find-and-show "riddley"))))
      (t/is (= "riddley/riddley 0.1.12\n  potemkin/potemkin 0.4.5\n    clj-http/clj-http 3.12.3\n"
               (with-out-str (fd/find-and-show "ridd"))))
      (t/is (= "Zero matches for abc\n"
               (with-out-str (fd/find-and-show "abc"))))))

#_(t/deftest all-keyword
    (with-redefs [tdio/slurp-edn (constantly {:deps {'com.github.liquidz/build.edn {:mvn/version "0.10.227"}}})]
      (def l (fd/get-libs))
      (let [libs (fd/get-libs)]
        (doseq [[k v] libs]
          (when (empty? (children libs k))
            (println k))))))
