module cascara.lang.yaml {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.lang.yaml;
    exports io.github.qishr.cascara.lang.yaml.annotation;
    exports io.github.qishr.cascara.lang.yaml.ast;
    exports io.github.qishr.cascara.lang.yaml.exception;
    exports io.github.qishr.cascara.lang.yaml.processor;
    exports io.github.qishr.cascara.lang.yaml.token;

    opens io.github.qishr.cascara.lang.yaml;
    opens io.github.qishr.cascara.lang.yaml.annotation;
    opens io.github.qishr.cascara.lang.yaml.ast;
    opens io.github.qishr.cascara.lang.yaml.exception;
    opens io.github.qishr.cascara.lang.yaml.processor;
    opens io.github.qishr.cascara.lang.yaml.token;
}
