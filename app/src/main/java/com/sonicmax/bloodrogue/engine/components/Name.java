package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Name component is used when we want to give an object a specific name (eg. for status text)
 */

public class Name extends Component {
    public String value;
    public String description;

    public Name(String name, String description, long id) {
        super(id);

        this.value = name;
        this.description = description;
    }
}
