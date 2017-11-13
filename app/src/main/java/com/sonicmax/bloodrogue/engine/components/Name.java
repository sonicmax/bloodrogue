package com.sonicmax.bloodrogue.engine.components;

import com.sonicmax.bloodrogue.engine.Component;

/**
 *  Name component is used when we want to give an object a specific name (eg. for status text)
 */

public class Name extends Component {
    public final String value;

    public Name(String name, long id) {
        super(id);

        this.value = name;
    }
}
