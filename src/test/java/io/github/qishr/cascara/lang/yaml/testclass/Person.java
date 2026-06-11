package io.github.qishr.cascara.lang.yaml.testclass;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.Serializable;
import io.github.qishr.cascara.lang.yaml.annotation.Init;

// Test class

@Serializable
public class Person {

    @DataField
    private String firstName;

    @DataField
    private String lastName;

    @DataField(key = "personAge")
    private String age;

    byte[] bytes;

    // TODO: Remove this constructor. The serializer shouldn't need it.
    public Person() {}

    public Person(String firstName, String lastName, String age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getAge() {
        return age;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] b) {
        bytes = b;
    }

    @Init
    private void initNames() {
        this.firstName = this.firstName.substring(0, 1).toUpperCase()
          + this.firstName.substring(1);
        this.lastName = this.lastName.substring(0, 1).toUpperCase()
          + this.lastName.substring(1);
    }
}
