package io.github.qishr.cascara.lang.yaml.testclass;

import java.util.UUID;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;

@Serializable
public class ColorDefinition {
    private static final String NAME_PLACEHOLDER = "<name>";

    /// Automatically assigned ID of this color or transform function
    // definition.
    @DataField
    String id = "";

    /// User-assigned name of the definition.
    @DataField
    String name = "";

    /// 6 or 8 digit hex representation of the color after processing.
    ///
    /// This can be set by the user, although it will be updated
    /// automatically if any transformation/processing is defined.
    @DataField
    String hexColor = "";

    /// Hex representation of left color input to transform.
    ///
    /// If leftHexColor, rightHexColor, and lerp are defined, hexColor
    /// will be automatically updated.
    @DataField
    String leftHexColor = "";

    /// Hex representation of right color input to transform.
    ///
    /// If leftHexColor, rightHexColor, and lerp are defined, hexColor
    /// will be automatically updated.
    @DataField
    String rightHexColor = "";

    /// Lerp value when mixing leftHexColor and rightHexColor.
    ///
    /// If leftHexColor, rightHexColor, and lerp are defined, hexColor
    /// will be automatically updated.
    @DataField
    String lerp = "";

    /// ID of base color that either defines this color or is input to
    /// a transform function.
    ///
    /// If transformId is defined, a transform function will be applied and
    /// baseColorId will be used as an input.
    /// If transformId is underfined, hexColor will be automatically set
    /// to the color referred to by baseColorId, unless paletteColorId is
    /// also assigned.
    @DataField
    String baseColorId = "";

    /// ID of transform function to use.
    ///
    /// When defined, hexColor will be set to the output of the transformation
    /// that transformId refers to.
    @DataField
    String transformId = "";

    /// ID of palette color that this color is defined by.
    ///
    /// When defined, hexColor will be set to the palette color referred
    /// to by paletteColorId. This takes priority when baseColorId is not
    /// being used as input to a transform function.
    @DataField
    String paletteColorId = "";

    /// String representation of a transform function.
    @DataField
    String transformDefinition = "";

    public ColorDefinition() {
        id = UUID.randomUUID().toString();
        name = NAME_PLACEHOLDER;
    }

    public String getId() {
        return id;
    }

    public String getLerp() {
        return lerp;
    }

    public void setLerp(String lerp) {
        this.lerp = lerp;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLeftHexColor() {
        return leftHexColor;
    }

    public void setLeftHexColor(String hexColor) {
        this.leftHexColor = hexColor;
    }

    public String getPaletteColorId() {
        return paletteColorId;
    }

    public void setPaletteColorId(String paletteItem) {
        this.paletteColorId = paletteItem;
    }

    public String getHexColor() {
        return hexColor;
    }

    public void setHexColor(String hexColor) {
        if (hexColor == null) {
            System.err.println("setHexColor: hexColor is null");
            Thread.dumpStack();
        }
        this.hexColor = hexColor;
    }

    public String getTransformId() {
        return transformId;
    }

    public void setTransformId(String transform) {
        this.transformId = transform;
    }

    public String getTransformDefinition() {
        return transformDefinition;
    }

    public void setTransformDefinition(String transformFunction) {
        this.transformDefinition = transformFunction;
    }

    public ColorDefinition duplicate() {
        ColorDefinition color = new ColorDefinition();
        color.setLeftHexColor(leftHexColor);
        color.setRightHexColor(rightHexColor);
        color.setLerp(lerp);
        color.setTransformId(transformId);
        color.setTransformDefinition(transformDefinition);
        color.setBaseColorId(baseColorId);
        color.setPaletteColorId(paletteColorId);
        color.setHexColor(hexColor);
        return color;
    }

    public String getBaseColorId() {
        return baseColorId;
    }

    public void setBaseColorId(String paletteColorDefinition) {
        this.baseColorId = paletteColorDefinition;
    }

    public String getRightHexColor() {
        return rightHexColor;
    }

    public void setRightHexColor(String hexColor) {
        this.rightHexColor = hexColor;
    }

    public boolean isBlank() {
        return hexColor == null || hexColor.isBlank();
    }

    public boolean usesTransform() {
        return transformId != null && !transformId.isBlank();
    }

    public boolean usesPaletteColor() {
        return paletteColorId != null && !paletteColorId.isBlank();
    }

    public boolean usesBaseColor() {
        return baseColorId != null && !baseColorId.isBlank();
    }

    public boolean usesLerp() {
        return lerp != null && !lerp.isBlank();
    }

    public boolean isEmpty() {
        return getHexColor().isEmpty() &&
                getTransformDefinition().isEmpty() &&
                getPaletteColorId().isEmpty() &&
                getBaseColorId().isEmpty() &&
                getTransformId().isEmpty() &&
                getLeftHexColor().isEmpty() &&
                getRightHexColor().isEmpty();
    }
}

