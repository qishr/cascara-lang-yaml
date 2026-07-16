module cascara.lang.yaml {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.lang.yaml.annotation;
    exports io.github.qishr.cascara.lang.yaml.ast;
    exports io.github.qishr.cascara.lang.yaml.exception;
    exports io.github.qishr.cascara.lang.yaml.processor;
    exports io.github.qishr.cascara.lang.yaml.token;
    exports io.github.qishr.cascara.lang.yaml.util;

    opens io.github.qishr.cascara.lang.yaml.annotation;
    opens io.github.qishr.cascara.lang.yaml.ast;
    opens io.github.qishr.cascara.lang.yaml.exception;
    opens io.github.qishr.cascara.lang.yaml.processor to cascara.common;
    opens io.github.qishr.cascara.lang.yaml.token;

    provides io.github.qishr.cascara.common.lang.processor.AstConverter
        with io.github.qishr.cascara.lang.yaml.processor.YamlConverter;

    provides io.github.qishr.cascara.common.lang.processor.Emitter
        with io.github.qishr.cascara.lang.yaml.processor.YamlEmitter;

    provides io.github.qishr.cascara.common.lang.processor.AstParser
        with io.github.qishr.cascara.lang.yaml.processor.YamlAstParser;

    provides io.github.qishr.cascara.common.lang.processor.Tokenizer
        with io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;

    // It also works this way...
    // provides io.github.qishr.cascara.common.service.ServiceProvider
    //     with io.github.qishr.cascara.lang.yaml.processor.YamlTokenizer;
}
