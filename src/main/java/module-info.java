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

    provides io.github.qishr.cascara.common.lang.processor.AstConverter
        with io.github.qishr.cascara.lang.yaml.processor.YamlConverter;
    provides io.github.qishr.cascara.common.lang.processor.Emitter
        with io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;
    provides io.github.qishr.cascara.common.lang.processor.Parser
        with io.github.qishr.cascara.lang.yaml.processor.YamlParser;
    // provides io.github.qishr.cascara.common.lang.processor.Tokenizer
    provides io.github.qishr.cascara.common.service.ServiceProvider
        with io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
}
