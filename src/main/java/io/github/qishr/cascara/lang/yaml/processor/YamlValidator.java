package io.github.qishr.cascara.lang.yaml.processor;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.processor.Validator;
import io.github.qishr.cascara.lang.yaml.exception.YamlParserException;

public class YamlValidator implements Validator {
    private final YamlParser parser = new YamlParser();

    /// Attaches a reporter to the internal parser.
    /// This allows the validator to participate in whatever
    /// diagnostic pipeline the caller (Studio, CLI, etc.) provides.
    public YamlValidator setReporter(Reporter reporter) {
        parser.setReporter(reporter);
        return this;
    }

    public ValidationResult validate(String content) {
        try {
            parser.parse(content);
            return new ValidationResult(true, "Valid YAML", -1);
        } catch (YamlParserException e) {
            // Assuming YamlParserException has line/column info
            return new ValidationResult(false, e.getMessage(), e.getLine());
        } catch (Exception e) {
            return new ValidationResult(false, "Unknown Error: " + e.getMessage(), 0);
        }
    }

    public record ValidationResult(boolean isValid, String message, int line) {
        public void printReport() {
            if (isValid) {
                System.out.println("✓ YAML is structurally sound.");
            } else {
                System.err.printf("✗ YAML Error at line %d: %s%n", line, message);
            }
        }
    }
}