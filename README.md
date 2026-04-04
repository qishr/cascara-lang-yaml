## 🛠 The Cascara YAML Processors

The YAML subsystem provides a high-fidelity lifecycle for YAML documents, ensuring that every space, comment, and structural nuance is preserved from input to output.

### 1. Structural Integrity & Parsing

The `YamlParser` enforces a strict-alignment model to eliminate ambiguity in complex documents:

- **Strict Indentation:** Tab characters (`\t`) are prohibited for indentation. Dedents must return exactly to a previously established column.

- **Sibling Alignment:** All keys in a mapping and all entry indicators (`-`) in a sequence must share the same starting column.

- **Context-Aware Scalars:** Plain scalars are restricted from containing structural characters (like `:`) to prevent mapping collision errors.


### 2. High-Fidelity Emission

The `YamlEmitter` is designed to produce human-readable, "clean" YAML that respects the original document's metadata:

- **Comment Preservation:** Supports three-tier comment placement:

  - **Header:** Document-level metadata.

  - **Leading:** Block comments bound to the node below.

  - **Trailing/Inline:** Same-line comments (e.g., `key: value # note`).

- **Collection Styling:** Intelligently toggles between **Block** (indented) and **Flow** (JSON-like `{}`) styles based on the AST node's `CollectionStyle` attribute.

- **Automatic Anchoring:** Properly emits `&anchor` and `*alias` nodes to maintain object graph identity.


### 3. Serialization Lifecycle

The `YamlSerializer` acts as the bridge between the Cascara generic `YamlDocument` and the underlying stream:

| **Feature** | **Behavior** |
| --- | --- |
| **Round-Tripping** | Guarantees that `emit(parse(text)) == text` for compliant documents. |
| **Whitespace Control** | Manages `NEWLINE` and `INDENT` token generation during emission to ensure valid block structure. |
| **BOM Support** | Automatically handles the UTF-8 Byte Order Mark (`\uFEFF`) if present in the source. |

### 4. Technical Constraints

- **Emitter Logic:** Always ensures a space follows a `VALUE_INDICATOR` (`:` ) and `SEQUENCE_ENTRY_INDICATOR` (`-` ) to satisfy the YAML spec.

- **Scalar Choice:** Automatically selects the safest quoting style (Plain, Single, or Double) based on character content if a specific style isn't requested.
