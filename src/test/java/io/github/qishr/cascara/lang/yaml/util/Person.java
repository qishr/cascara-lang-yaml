// # License & Terms
//
// This file is part of **Cascara**.
//
// **Cascara** is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
// ---
//
// ## Special Runtime Exception
//
// As a special exception, the copyright holders of this library give you
// permission to link this library with independent modules to produce an
// executable, regardless of the license terms of these independent modules,
// and to copy and distribute the resulting executable under terms of your
// choice, provided that you also meet, for each linked independent module,
// the terms and conditions of the license of that module.
//
// An independent module is a module which is not derived from or based on
// this library. If you modify this library, you may extend this exception
// to your version of the library, but you are not obligated to do so. If
// you do not wish to do so, delete this exception statement from your
// version.


package io.github.qishr.cascara.lang.yaml.util;

import io.github.qishr.cascara.common.annotation.DataField;

// Test class

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

    // private void initNames() {
    //     this.firstName = this.firstName.substring(0, 1).toUpperCase()
    //       + this.firstName.substring(1);
    //     this.lastName = this.lastName.substring(0, 1).toUpperCase()
    //       + this.lastName.substring(1);
    // }
}
