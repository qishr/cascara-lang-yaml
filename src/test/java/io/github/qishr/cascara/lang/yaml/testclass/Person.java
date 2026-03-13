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

    private String address;

    public Person(String firstName, String lastName, String age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
    }

    @Init
    private void initNames() {
        this.firstName = this.firstName.substring(0, 1).toUpperCase()
          + this.firstName.substring(1);
        this.lastName = this.lastName.substring(0, 1).toUpperCase()
          + this.lastName.substring(1);
    }

    // Standard getters and setters
}
